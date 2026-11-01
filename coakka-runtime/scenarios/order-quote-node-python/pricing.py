"""Application-owned sample catalog and quote rules; all money is integer cents."""
import json
import re

MAX_BYTES = 16 * 1024
MAX_ITEMS = 20
CATALOG = {"tea": 1250, "coffee": 1800, "cocoa": 2200}
CART_ID = re.compile(r"[A-Za-z0-9_-]{1,64}\Z")


class InvalidCart(ValueError):
    pass


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InvalidCart("Repeated field in cart.")
        result[key] = value
    return result


def calculate_quote(cart):
    if not isinstance(cart, dict) or set(cart) != {"cartId", "items"}:
        raise InvalidCart("Use cartId and items.")
    cart_id = cart["cartId"]
    if not isinstance(cart_id, str) or not CART_ID.fullmatch(cart_id):
        raise InvalidCart("cartId must be 1–64 letters, digits, underscores or hyphens.")
    items = cart["items"]
    if not isinstance(items, list) or not 1 <= len(items) <= MAX_ITEMS:
        raise InvalidCart("Use 1–20 cart items.")
    lines = []
    for item in items:
        if not isinstance(item, dict) or set(item) != {"sku", "quantity"}:
            raise InvalidCart("Each item needs sku and quantity.")
        sku, quantity = item["sku"], item["quantity"]
        if not isinstance(sku, str) or sku not in CATALOG:
            raise InvalidCart("Choose tea, coffee or cocoa.")
        if type(quantity) is not int or not 1 <= quantity <= 100:
            raise InvalidCart("quantity must be a whole number from 1 to 100.")
        unit_price = CATALOG[sku]
        lines.append({"sku": sku, "quantity": quantity,
                      "unitPriceCents": unit_price, "lineTotalCents": unit_price * quantity})
    subtotal = sum(line["lineTotalCents"] for line in lines)
    shipping = 0 if subtotal >= 5000 else 500
    return {"status": "quoted", "cartId": cart_id, "currency": "USD", "items": lines,
            "subtotalCents": subtotal, "shippingCents": shipping,
            "totalCents": subtotal + shipping}


def quote_payload(payload):
    """A rejected cart is a business reply, not a transport failure."""
    try:
        if len(payload) > MAX_BYTES:
            raise InvalidCart("Cart exceeds 16 KiB.")
        cart = json.loads(payload.decode("utf-8"), object_pairs_hook=unique_object)
        reply = calculate_quote(cart)
    except (InvalidCart, UnicodeDecodeError, json.JSONDecodeError, RecursionError, ValueError) as error:
        # Parser diagnostics can contain caller data; return a short application message.
        message = str(error) if isinstance(error, InvalidCart) else "Cart must be valid UTF-8 JSON."
        reply = {"status": "rejected", "code": "INVALID_CART", "message": message}
    return json.dumps(reply, ensure_ascii=True, allow_nan=False, separators=(",", ":")).encode("utf-8")
