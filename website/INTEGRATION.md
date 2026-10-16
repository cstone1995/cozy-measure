# Saved-room product sizing

Installed only on unpublished Shopify theme 206108197204. Do not publish that entire old draft theme: transfer the reviewed bridge asset and single layout script addition to the current live theme after approval.

The bridge stores original room dimensions/outline in the same browser for 30 days. On a product page it queries the existing Extendons calculator settings using only public shop/product/collection identifiers, not the room name or dimensions. It accepts metre-based Area L×W calculators with custom formulas off, 4m/5m bounds and valid minimum/maximum lengths. Other configurations fall back to manual entry. The third-party calculator remains responsible for prices and basket creation.

For supported products it compares purchased rectangular areas, adds the optional 10cm allowance once, rounds cut length upward to 10cm, applies the product minimum length, and rejects cuts over the maximum. It fills the existing #eo-sh-length / #eo-sh-width controls and triggers their input/change handlers. It does not overwrite a customer's edits made while settings load. Customers can reapply the saved sizes or change the allowance. Room dimensions are not replaced with the purchased roll dimensions. Native apps now send cozy_allowance=0/1; older handoffs default to allowance on.

Verified product configuration on 16 October 2026:

- Cozy Felt Dark Grey: metre units, Area L×W, 4m fixed width, length 2.5m–30m. Imported room 1.925m × 1.072m with allowance selected produces a 4m × 2.5m minimum cut, 10m², calculator £99.90. Tested through Add to cart: basket retains 2.5m length / 4m width / 10m² at £99.90. The added test basket line was removed; existing lines were preserved. The calculator may create its own area variant as part of its normal flow.
- Cozy Belmont Nutmeg: metre units, Area L×W, 4m/5m width bounds, length 2.5m–30m. A 4m × 3m room with allowance selects 5m × 3.1m (15.5m²); without allowance selects 4m × 3m (12m²). Tested the latter through Add to cart: basket retains 3m length / 4m width / 12m² at £281.88.

Eight pure Node tests cover malformed handoffs, outline geometry, cut choice/rounding, supported calculator settings, product minimum/maximum length and allowance flags. Browser checks are recorded in the PR description. Physical AR accuracy, permanent Android signing, iOS distribution and public launch remain separate acceptance requirements.

Limitations: no seam/pile/pattern planning; rotation must be reviewed. No price override or product-stock verification. Vendor configuration/network changes can make automatic filling unavailable; the saved room banner and manual calculator remain. No cross-device or cross-browser synchronization. Room URL parameters can reach server logs before they are removed after import. Product rules are loaded afresh per page. Theme scripts and calculator initialization can change; retest after app/theme updates.
