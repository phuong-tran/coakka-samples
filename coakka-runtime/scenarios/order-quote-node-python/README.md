# Node.js Checkout, Python Pricing

**Send a cart from Node.js. Get its quote from a separate Python process.**

A shop may use JavaScript for checkout and Python for its pricing rules. This
example keeps those rules in Python: Node sends a cart to the named work
`samples.order.quote`, and Python returns item prices, shipping and a total.
CoAkka Runtime carries the request and reply between the two processes.

Both apps use **published 3.0.0 packages** from npm and PyPI. Run them in two
terminals on one computer. Each app starts its own Runtime; no separate Runtime
server, Docker installation or Core build is needed.

## What you will see

The supplied cart contains two teas and one coffee:

| Item | Quantity | Unit price | Line total |
| --- | --- | --- | --- |
| Tea | 2 | USD 12.50 | USD 25.00 |
| Coffee | 1 | USD 18.00 | USD 18.00 |
| Shipping | | | USD 5.00 |
| **Quote** | | | **USD 48.00** |

These are fictional sample prices. Python charges USD 5 shipping below a
USD 50 subtotal; shipping is free from USD 50. Money is calculated in whole
cents. Nothing is purchased or stored.

## 1. Get the example and install packages

You need **Node.js 22 or later**, npm, and **standard CPython 3.11 or later**.
Use one of the [supported platforms](../../../docs/coakka-runtime/reference/runtime-package-platform-evidence.md).
Python and Node run on the same computer in this walkthrough.

If you already have this repository, use your existing checkout. Otherwise:

~~~sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples
~~~

From the repository root, enter the example:

~~~sh
cd coakka-runtime/scenarios/order-quote-node-python
npm ci
~~~

On **macOS or Linux**:

~~~sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
~~~

On **Windows PowerShell** (the command below selects Python 3.11):

~~~powershell
py -3.11 -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
~~~

No environment activation is needed. [package.json](package.json) pins
`coakka-v2-connector-node` to 3.0.0 and [requirements.txt](requirements.txt)
pins `coakka-v2-connector` to 3.0.0. The Node lockfile fixes its resolved package.

## 2. Terminal A: start Python pricing

Stay in the example directory. On macOS/Linux:

~~~sh
.venv/bin/python pricing_service.py
~~~

On Windows PowerShell:

~~~powershell
.venv\Scripts\python.exe pricing_service.py
~~~

Wait for:

~~~text
Pricing service ready at 127.0.0.1:19360
Press Enter or Ctrl+C to stop.
~~~

Keep this terminal open. The ready line appears after Runtime has started and
the pricing handler has been registered.

## 3. Terminal B: request a quote with Node

Open another terminal and enter the **same example directory** in your checkout.
Run:

~~~sh
npm run quote
~~~

The application prints:

~~~text
Quote cart-1001: USD 48.00
Calculated by the Python pricing service.
~~~

Node closes its Runtime and exits after the reply. Python stays available for
more carts. Run the Node command again, or try several Node terminals together.
Each caller has its own instance name; all ask the same pricing target.

For the complete result, including line prices and shipping:

~~~sh
node client.mjs --json
~~~

## Change the cart

Edit [cart.json](cart.json), then run the Node command again. For example, four
teas give a USD 50.00 subtotal and free shipping. Python also knows `coffee`
(USD 18.00) and `cocoa` (USD 22.00).

A cart needs a short `cartId` and 1–20 items. Each item has `sku` and a whole
`quantity` from 1 to 100. The JSON input is limited to 16 KiB. An unknown product
or invalid quantity receives a normal business reply, such as:

~~~text
Cart rejected: Choose tea, coffee or cocoa.
~~~

To use another cart file:

~~~sh
node client.mjs --cart my-cart.json
~~~

Node exits with 0 for a quote, 2 for a rejected cart and 1 when it cannot obtain
a usable result. A rejected cart means Python answered; an unavailable service
or expired request is a different failure. The demo does not retry a request.

## Stop and run tests

In Terminal A, press **Enter** or **Ctrl+C**. Python closes Runtime and prints
`Pricing service stopped.` Node needs no separate stop command after a quote.

From the example directory, with packages installed:

~~~sh
npm test
~~~

This works in PowerShell too. Tests choose the Python executable in `.venv`;
if you keep your environment elsewhere, set `PYTHON` to that executable.
Tests start and stop their own Python/Node processes on available local ports.
They cover pricing rules, malformed input, concurrent callers, rejected carts,
missing routes/services, request deadlines, interrupted work and clean shutdown.
Only the test fixture simulates slow or failing business work.

## If it does not run

| Symptom | What to check |
| --- | --- |
| Python has no ready line | Confirm its package installation and Python version. Port 19360 may already be in use. |
| Node says quote unavailable | Start Python first; use the same port in both terminals. Also check that the cart file is readable JSON. |
| Cart is rejected | Read the returned message and correct its product or quantity. |
| You need another port | Start Python with `pricing_service.py --port 19361`, then Node with `node client.mjs --port 19361`. |
| Python exits immediately | Keep its terminal input open. End-of-input also stops the service. |

The example listens only on `127.0.0.1`. It teaches two processes on one
computer; a multi-machine deployment needs its own network and security setup.

## Read the code and reuse the pattern

| File | Responsibility |
| --- | --- |
| [client.mjs](client.mjs) | Read a cart, show the result and choose a CLI exit status. |
| [quote-client.mjs](quote-client.mjs) | Start the Node Runtime, ask Python for a quote and close. |
| [pricing_service.py](pricing_service.py) | Start the Python Runtime and register the pricing handler. |
| [pricing.py](pricing.py) | Validate the cart, apply catalog/shipping rules and return JSON. |
| [Integration tests](tests/integration.test.mjs) | Exercise the actual released packages across processes. |
| [Pricing tests](tests/pricing_test.py) | Check business rules and input boundaries. |

The target is the name of the work, not a process name. Node's route tells
Runtime where Python owns that work; Python registers its handler for the same
target. Each application owns its Runtime lifetime. Runtime owns delivery,
reply matching and request deadlines. Python owns the pricing decision.
Both sides exchange ordinary JSON application data.

AUTO CPU selection, MULTIPLEXED connections and monitoring off remain the
Runtime defaults. The demo configures only its identities, target and local
network address. See [defaults and monitoring](../../../docs/coakka-runtime/operations/runtime-monitoring.md)
when you need to inspect or change those settings.

Next: [integrate Runtime in your app](../../../docs/coakka-runtime/guides/runtime-integration-guide.md)
or [choose another use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md).
[Runtime samples](../../README.md) · [All products](../../../README.md).
