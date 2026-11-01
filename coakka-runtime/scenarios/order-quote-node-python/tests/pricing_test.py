import json
import random
import unittest
from pricing import MAX_BYTES, calculate_quote, quote_payload, InvalidCart


def cart(items=None):
    return {"cartId": "cart-1001", "items": items or [{"sku": "tea", "quantity": 2}, {"sku": "coffee", "quantity": 1}]}


class PricingTests(unittest.TestCase):
    def test_example(self):
        q = calculate_quote(cart())
        self.assertEqual((q["subtotalCents"], q["shippingCents"], q["totalCents"]), (4300, 500, 4800))
        self.assertEqual(q["items"][0]["lineTotalCents"], 2500)

    def test_shipping_threshold(self):
        for quantity, shipping in [(3, 500), (4, 0)]:
            with self.subTest(quantity=quantity):
                q = calculate_quote(cart([{"sku": "tea", "quantity": quantity}]))
                self.assertEqual(q["shippingCents"], shipping)

    def test_item_and_quantity_bounds(self):
        q = calculate_quote(cart([{"sku": "cocoa", "quantity": 100}] * 20))
        self.assertEqual(q["totalCents"], 4400000)
        for quantity in [True, False, 0, -1, 101, 1.5, "2", None]:
            with self.subTest(quantity=quantity), self.assertRaises(InvalidCart):
                calculate_quote(cart([{"sku": "tea", "quantity": quantity}]))
        for items in [[], [{}] * 21, {}, None]:
            with self.subTest(items=items), self.assertRaises(InvalidCart):
                calculate_quote({"cartId": "x", "items": items})

    def test_schema(self):
        invalid = [None, [], 1, {}, {**cart(), "extra": 1},
                   cart([{"sku": "unknown", "quantity": 1}]),
                   cart([{"sku": [], "quantity": 1}]),
                   cart([{"sku": "tea", "quantity": 1, "extra": 1}])]
        invalid += [{**cart(), "cartId": value} for value in [1, True, "", "x" * 65, "a/b", "a\n", "café"]]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(InvalidCart):
                calculate_quote(value)

    def test_payload_failures(self):
        inputs = [b"{", b"\xff", b" " * (MAX_BYTES + 1), b"[" * 2000,
                  b'{"cartId":"a","cartId":"b","items":[]}',
                  b'{"cartId":"a","items":[{"sku":"tea","quantity":NaN}]}',
                  ('{"cartId":"a","items":[{"sku":"tea","quantity":' + '9' * 5000 + '}]}').encode()]
        for payload in inputs:
            with self.subTest(size=len(payload)):
                result = json.loads(quote_payload(payload))
                self.assertEqual((result["status"], result["code"]), ("rejected", "INVALID_CART"))
                self.assertLess(len(quote_payload(payload)), 512)

    def test_deterministic_malformed_corpus(self):
        rng = random.Random(554)
        for _ in range(500):
            payload = bytes(rng.randrange(256) for _ in range(rng.randrange(512)))
            reply = json.loads(quote_payload(payload))
            self.assertEqual(reply["status"], "rejected")

    def test_utf8_and_byte_limit(self):
        raw = json.dumps(cart()).encode()
        self.assertEqual(json.loads(quote_payload(raw + b" " * (MAX_BYTES - len(raw))))["totalCents"], 4800)
        self.assertEqual(json.loads(quote_payload(raw + b" " * (MAX_BYTES - len(raw) + 1)))["status"], "rejected")


if __name__ == "__main__":
    unittest.main()
