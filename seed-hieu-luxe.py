#!/usr/bin/env python3
"""
Seed HIEU-aesthetic catalog — handbags, shoes, watches, leather goods,
apparel — with a FULL attribute matrix (size × color where applicable).

Every color gets a unique luxury Unsplash photo so the storefront variant
picker behaves like Shopee: tapping "Đen" swaps the hero gallery to the black
colorway, "Đỏ" swaps to crimson, etc. Size variants reuse the colorway image
since size doesn't change appearance.

Pre-req: seed.sh has been run (users + 5 root categories + 5 attrs).

Run:  python3 seed-hieu-luxe.py [--wipe]
        --wipe   delete every existing product first (admin only)
"""
import json
import sys
import urllib.error
import urllib.request
from itertools import product as cartesian

AUTH = "http://localhost:8081"
CATALOG = "http://localhost:8083"

# Stable IDs after seed.sh ─────────────────────────────────────────────
SIZE = {"S": 1, "M": 2, "L": 3, "XL": 4, "XXL": 5}
COLOR = {"Đen": 6, "Trắng": 7, "Đỏ": 8, "Xanh": 9, "Xám": 10}
STORAGE = {"64GB": 11, "128GB": 12, "256GB": 13, "512GB": 14, "1TB": 15}
RAM = {"4GB": 16, "8GB": 17, "16GB": 18, "32GB": 19}

ATTR_SIZE = 1
ATTR_COLOR = 2
ATTR_STORAGE = 3
ATTR_RAM = 4

CAT_QUAN_AO = 10
CAT_DIEN_THOAI = 11
CAT_LAPTOP = 12
CAT_GIAY_DEP = 13
CAT_PHU_KIEN = 14


def img(uid: str, w: int = 900) -> str:
    return f"https://images.unsplash.com/photo-{uid}?w={w}&q=80&auto=format&fit=crop"


# ─────────────────────────────────────────────────────────────────────
# Curated image library — every entry was HEAD-checked, all 200.
# Grouped by silhouette so we can pick "another black bag photo" easily.
# ─────────────────────────────────────────────────────────────────────
IMG = {
    # Bags — totes / structured
    "bag_tote_cream": img("1584917865442-de89df76afd3"),
    "bag_tote_black": img("1591561954557-26941169b49e"),
    "bag_tote_caramel": img("1548036328-c9fa89d128fa"),
    "bag_tote_olive": img("1564859228273-274232fdb516"),
    "bag_tote_dark": img("1622560480605-d83c853bc5c3"),
    # Bags — bucket / hobo
    "bag_bucket_black": img("1564422170194-896b89110ef8"),
    "bag_bucket_navy": img("1605733160314-4fc7dac4bb16"),
    "bag_bucket_oxblood": img("1599643477877-530eb83abc8e"),
    "bag_bucket_neutral": img("1599839619722-39751411ea63"),
    # Bags — clutches / evening
    "bag_clutch_black": img("1601924994987-69e26d50dc26"),
    "bag_clutch_crimson": img("1559563458-527698bf5295"),
    "bag_clutch_red": img("1492707892479-7bc8d5a4ee93"),
    "bag_clutch_cream": img("1614094082869-cd4e4b2905c7"),
    "bag_clutch_emerald": img("1551489186-cf8726f514f8"),
    "bag_clutch_dark": img("1605733513597-a8f8341084e6"),
    # Bags — chain / shoulder / cross-body
    "bag_chain_gold": img("1606522754091-a3bbf9ad4cb3"),
    "bag_chain_caramel": img("1503602642458-232111445657"),
    "bag_chain_blush": img("1605408499391-6368c628ef42"),
    "bag_chain_silver": img("1556306535-0f09a537f0a3"),
    "bag_chain_noir": img("1591348122449-02525d70379b"),
    "bag_chain_mint": img("1551316679-9c6ae9dec224"),
    "bag_chain_taupe": img("1503342217505-b0a15ec3261c"),
    "bag_chain_walnut": img("1620794108219-aedbaded4eea"),
    # Shoes
    "shoe_heel_red": img("1543163521-1bf539c55dd2"),
    "shoe_heel_nude": img("1549298916-b41d501d3772"),
    "shoe_heel_black": img("1525966222134-fcfa99b8ae77"),
    "shoe_heel_burgundy": img("1518049362265-d5b2a6b00b37"),
    "shoe_oxford_black": img("1614253429340-98120bd6d753"),
    "shoe_oxford_walnut": img("1595950653106-6c9ebd614d3a"),
    "shoe_sneaker_white": img("1606107557195-0e29a4b5b4aa"),
    # Watches
    "watch_silver": img("1523275335684-37898b6baf30"),
    "watch_chrono_black": img("1547996160-81dfa63595aa"),
    "watch_rose_gold": img("1622434641406-a158123450f9"),
    "watch_classic": img("1524805444758-089113d48a6d"),
    # Wallets / leather goods
    "wallet_caramel": img("1627123424574-724758594e93"),
    "wallet_black": img("1601925260368-ae2f83cf8b7f"),
    "wallet_saffiano": img("1620916566398-39f1143ab7be"),
    # Sunglasses + accessories
    "sunglasses_black": img("1600185365926-3a2ce3cdb9eb"),
    # Apparel
    "coat_camel": img("1539109136881-3be0616acf4b"),
    "knit_cream": img("1567401893414-76b7b1e5a7a5"),
    "knit_charcoal": img("1551803091-e20673f15770"),
    "knit_burgundy": img("1485231183945-fffde7cc051e"),
    "knit_olive": img("1542838132-92c53300491e"),
    "blazer_black": img("1591047139829-d91aecb6caea"),
    "blazer_taupe": img("1611078489935-0cb964de46d6"),
    "trouser_charcoal": img("1495121605193-b116b5b9c5fe"),
    "trouser_cream": img("1626278664285-f796b9ee7806"),
    "tee_white": img("1485182708500-e8f1f318ba72"),
    "tee_charcoal": img("1517363898874-737b62a7db91"),
}


def login_admin() -> str:
    """Log in admin user, return JWT from Set-Cookie."""
    req = urllib.request.Request(
        f"{AUTH}/api/v1/auth/login",
        data=json.dumps({"usernameOrEmail": "admin", "password": "Admin@2026"}).encode(),
        method="POST",
    )
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=20) as r:
        for h, v in r.headers.items():
            if h.lower() == "set-cookie" and "ACCESS_TOKEN=" in v:
                return v.split("ACCESS_TOKEN=", 1)[1].split(";", 1)[0]
    raise RuntimeError("No ACCESS_TOKEN in login response — make sure seed.sh has run.")


def wipe_existing(token: str):
    """DELETE every existing product so re-seeding is idempotent."""
    req = urllib.request.Request(f"{CATALOG}/api/v1/products?size=500", method="GET")
    req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=20) as r:
        data = json.loads(r.read().decode())
    ids = [p["id"] for p in data.get("items", data.get("content", []))]
    print(f"🧹 Wiping {len(ids)} existing products…")
    for pid in ids:
        try:
            req = urllib.request.Request(f"{CATALOG}/api/v1/products/{pid}", method="DELETE")
            req.add_header("Authorization", f"Bearer {token}")
            urllib.request.urlopen(req, timeout=10).read()
        except urllib.error.HTTPError as e:
            print(f"  · #{pid}: HTTP {e.code}", file=sys.stderr)
    print()


def matrix(sku_prefix: str, base_price: int, colors: dict, sizes: list, qty_per: int = 8, sale: int | None = None):
    """Build a full color × size variant matrix.

    `colors` is `{color_id: image_url}`; `sizes` is `[size_id, ...]`.
    Each combination becomes one variant; the image follows the color.
    Pass `sizes=[]` to build a color-only matrix.
    """
    out = []
    color_pairs = list(colors.items())
    size_list = sizes if sizes else [None]
    for color_id, image in color_pairs:
        for size_id in size_list:
            attrs = [{"attrId": ATTR_COLOR, "attrValId": color_id}]
            if size_id is not None:
                attrs.append({"attrId": ATTR_SIZE, "attrValId": size_id})
            sku_parts = [sku_prefix, f"C{color_id}"]
            if size_id is not None:
                sku_parts.append(f"S{size_id}")
            v = {
                "sku": "-".join(sku_parts),
                "price": base_price,
                "quantity": qty_per,
                "image": image,
                "attrs": attrs,
            }
            if sale:
                v["salePrice"] = sale
            out.append(v)
    return out


def size_only(sku_prefix: str, base_price: int, image: str, sizes: list, qty_per: int = 12, sale: int | None = None):
    """Size-only matrix (single colorway)."""
    return [
        {
            "sku": f"{sku_prefix}-S{sid}",
            "price": base_price,
            "quantity": qty_per,
            "image": image,
            "attrs": [{"attrId": ATTR_SIZE, "attrValId": sid}],
            **({"salePrice": sale} if sale else {}),
        }
        for sid in sizes
    ]


def product(name: str, desc: str, brand: str, cat_id: int, variants: list, images: list[str], thumb: str | None = None):
    """Assemble the product DTO. `images` becomes the gallery."""
    thumbnail = thumb or images[0]
    deduped = []
    for u in [thumbnail] + images:
        if u and u not in deduped:
            deduped.append(u)
    return {
        "name": name,
        "description": desc,
        "brand": brand,
        "categoryId": cat_id,
        "thumbnail": thumbnail,
        "images": deduped,
        "activate": True,
        "variants": variants,
    }


def post_product(token: str, p: dict) -> int | None:
    body = json.dumps(p).encode()
    req = urllib.request.Request(f"{CATALOG}/api/v1/products", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            resp = json.loads(r.read().decode())
            return resp.get("data", resp).get("id")
    except urllib.error.HTTPError as e:
        print(f"  ✗ {p['name']:<42}  HTTP {e.code}: {e.read().decode()[:200]}", file=sys.stderr)
        return None


def build_catalog() -> list[dict]:
    """Return the 25 HIEU products with their full variant matrices."""
    products: list[dict] = []

    # ───── HANDBAGS (CAT_PHU_KIEN) ─────
    p1_colors = {
        COLOR["Trắng"]: IMG["bag_tote_cream"],
        COLOR["Đen"]: IMG["bag_tote_black"],
        COLOR["Xám"]: IMG["bag_tote_caramel"],
        COLOR["Xanh"]: IMG["bag_tote_olive"],
        COLOR["Đỏ"]: IMG["bag_tote_dark"],
    }
    products.append(product(
        "Hieu Giglio Flora — Large Tote",
        "Generation Hieu pulls from the maison's archival visual codes. The Giglio's tote-inspired silhouette is crafted from Floral net with leather trim. Five archival colorways, hand-finished in Florence.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-GIGLIO", 68500000, p1_colors, [], qty_per=6),
        list(p1_colors.values()),
    ))

    p2_colors = {
        COLOR["Đen"]: IMG["bag_bucket_black"],
        COLOR["Xanh"]: IMG["bag_bucket_navy"],
        COLOR["Đỏ"]: IMG["bag_bucket_oxblood"],
        COLOR["Trắng"]: IMG["bag_bucket_neutral"],
        COLOR["Xám"]: IMG["bag_tote_caramel"],
    }
    products.append(product(
        "Borsetto Boston — Medium",
        "A travel-friendly boston shape rendered in deep-grain leather. Detachable shoulder strap, suede-lined interior with two zip pockets.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-BORSETTO", 49000000, p2_colors, [], qty_per=8),
        list(p2_colors.values()),
    ))

    p3_colors = {
        COLOR["Đen"]: IMG["bag_clutch_black"],
        COLOR["Đỏ"]: IMG["bag_clutch_crimson"],
        COLOR["Trắng"]: IMG["bag_clutch_cream"],
        COLOR["Xám"]: IMG["bag_clutch_dark"],
        COLOR["Xanh"]: IMG["bag_clutch_emerald"],
    }
    products.append(product(
        "Marmont Matelassé — Evening Clutch",
        "Hand-quilted matelassé chevron in glossy lambskin. Hidden snap closure, satin pouch included. The signature piece of the evening line.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-MARMONT", 84500000, p3_colors, [], qty_per=4, sale=78000000),
        list(p3_colors.values()) + [IMG["bag_clutch_red"]],
    ))

    p4_colors = {
        COLOR["Đen"]: IMG["bag_chain_noir"],
        COLOR["Trắng"]: IMG["bag_chain_blush"],
        COLOR["Xám"]: IMG["bag_chain_silver"],
        COLOR["Đỏ"]: IMG["bag_chain_gold"],
        COLOR["Xanh"]: IMG["bag_chain_mint"],
    }
    products.append(product(
        "Tribeca Chain — Shoulder Bag",
        "Slimline shoulder bag with antique-gold chain. Magnetic flap, suede interior compartment, fits passport and phone.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-TRIBECA", 56500000, p4_colors, [], qty_per=6),
        list(p4_colors.values()),
    ))

    p5_colors = {
        COLOR["Xám"]: IMG["bag_chain_caramel"],
        COLOR["Đen"]: IMG["bag_chain_walnut"],
        COLOR["Trắng"]: IMG["bag_chain_taupe"],
    }
    products.append(product(
        "Padlock Mini — Cross-Body",
        "Compact cross-body in tumbled calfskin with engraved padlock charm. Adjustable strap, soft micro-suede lining.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-PADLOCK", 38500000, p5_colors, [], qty_per=10, sale=33500000),
        list(p5_colors.values()),
    ))

    # ───── SHOES (CAT_GIAY_DEP) — full size × color matrix ─────
    p6_colors = {
        COLOR["Đen"]: IMG["shoe_heel_black"],
        COLOR["Đỏ"]: IMG["shoe_heel_red"],
        COLOR["Trắng"]: IMG["shoe_heel_nude"],
        COLOR["Xám"]: IMG["shoe_heel_burgundy"],
    }
    p6_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Marlena Pump — 95mm",
        "A polished pump in supple lambskin with a 95mm Italian stiletto heel. Padded leather insole, leather sole with rubber forepart for traction.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-MARLENA", 18900000, p6_colors, p6_sizes, qty_per=6, sale=16500000),
        list(p6_colors.values()),
    ))

    p7_colors = {
        COLOR["Đen"]: IMG["shoe_oxford_black"],
        COLOR["Xám"]: IMG["shoe_oxford_walnut"],
    }
    p7_sizes = [SIZE["M"], SIZE["L"], SIZE["XL"], SIZE["XXL"]]
    products.append(product(
        "Carlton Oxford — Burnished Calf",
        "Hand-burnished oxford in calf leather. Goodyear-welted construction. Leather sole with rubber heel pad.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-CARLTON", 24500000, p7_colors, p7_sizes, qty_per=5),
        list(p7_colors.values()),
    ))

    p8_colors = {
        COLOR["Xám"]: IMG["shoe_oxford_walnut"],
        COLOR["Đen"]: IMG["shoe_oxford_black"],
    }
    p8_sizes = [SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Venezia Loafer — Horsebit",
        "Polished loafer with maison horsebit hardware. Burnished cognac calf, slim leather sole.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-VENEZIA", 21900000, p8_colors, p8_sizes, qty_per=6, sale=19500000),
        list(p8_colors.values()),
    ))

    p9_colors = {
        COLOR["Trắng"]: IMG["shoe_sneaker_white"],
        COLOR["Đen"]: IMG["shoe_oxford_black"],
    }
    p9_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"], SIZE["XXL"]]
    products.append(product(
        "Hieu Court Sneaker",
        "Low-top court sneaker in tumbled calf with painted edges and serrato sole. Made in Italy.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-COURT", 18500000, p9_colors, p9_sizes, qty_per=8),
        list(p9_colors.values()),
    ))

    p10_colors = {
        COLOR["Đỏ"]: IMG["shoe_heel_red"],
        COLOR["Đen"]: IMG["shoe_heel_black"],
        COLOR["Trắng"]: IMG["shoe_heel_nude"],
    }
    p10_sizes = [SIZE["S"], SIZE["M"], SIZE["L"]]
    products.append(product(
        "Carrara Slingback — 65mm",
        "Slingback with adjustable buckle and squared toe. Italian kid leather, 65mm covered heel.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-CARRARA", 16500000, p10_colors, p10_sizes, qty_per=7),
        list(p10_colors.values()),
    ))

    # ───── WATCHES (CAT_PHU_KIEN) ─────
    p11_colors = {
        COLOR["Đen"]: IMG["watch_chrono_black"],
        COLOR["Xám"]: IMG["watch_silver"],
        COLOR["Đỏ"]: IMG["watch_rose_gold"],
    }
    products.append(product(
        "Maison 1953 — Chronograph",
        "Swiss-made chronograph with sapphire crystal and 41mm steel case. 100m water resistance, automatic movement, exhibition caseback.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-1953", 89000000, p11_colors, [], qty_per=5, sale=82000000),
        list(p11_colors.values()),
    ))

    p12_colors = {
        COLOR["Xám"]: IMG["watch_classic"],
        COLOR["Đen"]: IMG["watch_chrono_black"],
        COLOR["Đỏ"]: IMG["watch_rose_gold"],
    }
    products.append(product(
        "Atelier Classic — 38mm",
        "Slim dress watch with hand-applied indices. Italian leather strap, 4Hz movement.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-ATELIER", 42000000, p12_colors, [], qty_per=7),
        list(p12_colors.values()),
    ))

    # ───── LEATHER GOODS (CAT_PHU_KIEN) ─────
    p13_colors = {
        COLOR["Đen"]: IMG["wallet_saffiano"],
        COLOR["Xám"]: IMG["wallet_caramel"],
        COLOR["Đỏ"]: IMG["wallet_black"],
    }
    products.append(product(
        "Card Holder — Saffiano",
        "Compact card holder in saffiano leather with embossed maison signature. Six card slots and a central pocket.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-CARD", 5500000, p13_colors, [], qty_per=24, sale=4900000),
        list(p13_colors.values()),
    ))

    p14_colors = {
        COLOR["Đen"]: IMG["wallet_black"],
        COLOR["Xám"]: IMG["wallet_caramel"],
    }
    products.append(product(
        "Continental Wallet",
        "Long wallet in box-calf leather. Twelve card slots, two bill compartments, zipped change pocket.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-CONT", 12500000, p14_colors, [], qty_per=14),
        list(p14_colors.values()),
    ))

    # ───── ACCESSORIES ─────
    p15_colors = {COLOR["Đen"]: IMG["sunglasses_black"]}
    products.append(product(
        "Riviera Sunglasses",
        "Oversized acetate sunglasses with gradient lenses. UV400 protection, gold-toned hinges.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-RIVIERA", 9500000, p15_colors, [], qty_per=20),
        list(p15_colors.values()),
    ))

    # ───── APPAREL (CAT_QUAN_AO) — full size × color ─────
    p16_colors = {
        COLOR["Xám"]: IMG["coat_camel"],
        COLOR["Đen"]: IMG["blazer_black"],
    }
    p16_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Maison Camel Coat",
        "Double-faced wool-cashmere coat in tonal camel. Notch lapel, set-in sleeves, side welt pockets.",
        "HIEU", CAT_QUAN_AO,
        matrix("HIEU-CAMEL", 38000000, p16_colors, p16_sizes, qty_per=6),
        list(p16_colors.values()),
    ))

    p17_colors = {
        COLOR["Trắng"]: IMG["knit_cream"],
        COLOR["Đen"]: IMG["knit_charcoal"],
        COLOR["Đỏ"]: IMG["knit_burgundy"],
        COLOR["Xanh"]: IMG["knit_olive"],
    }
    p17_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Cashmere Knit — Crewneck",
        "Pure cashmere crewneck in undyed cream and three archival colorways. Ribbed cuffs and hem.",
        "HIEU", CAT_QUAN_AO,
        matrix("HIEU-CASH", 14500000, p17_colors, p17_sizes, qty_per=8, sale=12500000),
        list(p17_colors.values()),
    ))

    p18_colors = {
        COLOR["Đen"]: IMG["blazer_black"],
        COLOR["Xám"]: IMG["blazer_taupe"],
    }
    p18_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Atelier Blazer — Wool Twill",
        "Single-breasted blazer in pure wool twill. Half-canvas construction, mother-of-pearl buttons.",
        "HIEU", CAT_QUAN_AO,
        matrix("HIEU-BLAZER", 32500000, p18_colors, p18_sizes, qty_per=5),
        list(p18_colors.values()),
    ))

    p19_colors = {
        COLOR["Xám"]: IMG["trouser_charcoal"],
        COLOR["Trắng"]: IMG["trouser_cream"],
    }
    p19_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Tailored Trouser — Wool",
        "Mid-rise tailored trouser in stretch wool. Flat-front, full break, side adjusters.",
        "HIEU", CAT_QUAN_AO,
        matrix("HIEU-TROUSER", 14500000, p19_colors, p19_sizes, qty_per=10),
        list(p19_colors.values()),
    ))

    p20_colors = {
        COLOR["Trắng"]: IMG["tee_white"],
        COLOR["Đen"]: IMG["tee_charcoal"],
    }
    p20_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"], SIZE["XXL"]]
    products.append(product(
        "Maison Cotton Tee",
        "Heavyweight Pima cotton tee with embroidered maison logo. Pre-washed for an even drape.",
        "HIEU", CAT_QUAN_AO,
        matrix("HIEU-TEE", 3500000, p20_colors, p20_sizes, qty_per=18, sale=2890000),
        list(p20_colors.values()),
    ))

    # ───── MORE BAGS to round out the catalog ─────
    p21_colors = {
        COLOR["Đen"]: IMG["bag_clutch_dark"],
        COLOR["Trắng"]: IMG["bag_clutch_cream"],
        COLOR["Đỏ"]: IMG["bag_clutch_red"],
    }
    products.append(product(
        "Diana Soft Hobo — Medium",
        "Slouchy hobo silhouette in soft pebbled calfskin with bamboo-detailed handle. Magnetic top closure.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-DIANA", 58500000, p21_colors, [], qty_per=6),
        list(p21_colors.values()),
    ))

    p22_colors = {
        COLOR["Đen"]: IMG["bag_bucket_black"],
        COLOR["Xám"]: IMG["bag_chain_taupe"],
    }
    products.append(product(
        "Soho Disco — Round Cross-body",
        "Iconic round disco bag with tassel zip pull. Adjustable cross-body strap, slip pocket interior.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-SOHO", 42500000, p22_colors, [], qty_per=8, sale=39500000),
        list(p22_colors.values()),
    ))

    p23_colors = {
        COLOR["Đen"]: IMG["bag_chain_walnut"],
        COLOR["Trắng"]: IMG["bag_chain_blush"],
        COLOR["Xám"]: IMG["bag_chain_silver"],
        COLOR["Đỏ"]: IMG["bag_chain_gold"],
    }
    products.append(product(
        "Sylvie Top-Handle — Small",
        "Top-handle bag with web stripe and brushed-steel buckle. Detachable shoulder strap.",
        "HIEU", CAT_PHU_KIEN,
        matrix("HIEU-SYLVIE", 64500000, p23_colors, [], qty_per=5),
        list(p23_colors.values()),
    ))

    # ───── MORE SHOES ─────
    p24_colors = {
        COLOR["Đen"]: IMG["shoe_heel_black"],
        COLOR["Đỏ"]: IMG["shoe_heel_burgundy"],
    }
    p24_sizes = [SIZE["S"], SIZE["M"], SIZE["L"]]
    products.append(product(
        "Aria Ankle Boot — 70mm",
        "Sleek ankle boot with covered heel and side zip. Italian box-calf leather, leather sole.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-ARIA", 22500000, p24_colors, p24_sizes, qty_per=6, sale=19900000),
        list(p24_colors.values()),
    ))

    p25_colors = {
        COLOR["Xám"]: IMG["shoe_oxford_walnut"],
        COLOR["Đen"]: IMG["shoe_oxford_black"],
        COLOR["Trắng"]: IMG["shoe_sneaker_white"],
    }
    p25_sizes = [SIZE["S"], SIZE["M"], SIZE["L"], SIZE["XL"]]
    products.append(product(
        "Lido Driving Shoe",
        "Hand-stitched moccasin with rubber-studded sole. Soft suede upper, padded leather insole.",
        "HIEU", CAT_GIAY_DEP,
        matrix("HIEU-LIDO", 14500000, p25_colors, p25_sizes, qty_per=8),
        list(p25_colors.values()),
    ))

    return products


def main():
    wipe = "--wipe" in sys.argv
    token = login_admin()
    print(f"✓ Logged in (token len={len(token)})")

    if wipe:
        wipe_existing(token)

    products = build_catalog()
    print(f"📦 Seeding {len(products)} products …\n")
    success = 0
    for p in products:
        pid = post_product(token, p)
        if pid is not None:
            success += 1
            vc = len(p["variants"])
            tq = sum(v["quantity"] for v in p["variants"])
            colors = {a["attrValId"] for v in p["variants"] for a in v["attrs"] if a["attrId"] == ATTR_COLOR}
            sizes = {a["attrValId"] for v in p["variants"] for a in v["attrs"] if a["attrId"] == ATTR_SIZE}
            print(f"  ✓ #{pid:>3}  {p['name']:<42}  {vc} variants  ({len(colors)} colors × {len(sizes) or 1} sizes)  stock={tq}")

    print(f"\n✅ {success}/{len(products)} products seeded.")
    print("   http://localhost:3000/shop")
    print("   http://localhost:3000/product?id=<first id above>\n")


if __name__ == "__main__":
    main()
