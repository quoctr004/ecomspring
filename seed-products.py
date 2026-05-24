#!/usr/bin/env python3
"""
Seed products with REAL variant attributes (size/color/storage/ram).

Pre-req: admin user exists + attrs (size/color/storage/ram/material) +
categories (Quần áo/Điện thoại/Laptop/Giày dép/Phụ kiện) are already seeded
via seed.sh.

Run with:  python3 seed-products.py
"""
import json
import sys
import time
import urllib.request
import urllib.error

AUTH = "http://localhost:8081"
CATALOG = "http://localhost:8083"
VOUCHER = "http://localhost:8094"

# Hard-coded IDs from previous seed run (visible via /admin/attrs + /admin/categories).
ATTR_SIZE = 1     # values: S=1, M=2, L=3, XL=4, XXL=5
ATTR_COLOR = 2    # values: Đen=6, Trắng=7, Đỏ=8, Xanh=9, Xám=10
ATTR_STORAGE = 3  # 64GB=11, 128GB=12, 256GB=13, 512GB=14, 1TB=15
ATTR_RAM = 4      # 4GB=16, 8GB=17, 16GB=18, 32GB=19

SIZE = {"S": 1, "M": 2, "L": 3, "XL": 4, "XXL": 5}
COLOR = {"Đen": 6, "Trắng": 7, "Đỏ": 8, "Xanh": 9, "Xám": 10}
STORAGE = {"64GB": 11, "128GB": 12, "256GB": 13, "512GB": 14, "1TB": 15}
RAM = {"4GB": 16, "8GB": 17, "16GB": 18, "32GB": 19}

CAT_QUAN_AO = 10
CAT_DIEN_THOAI = 11
CAT_LAPTOP = 12
CAT_GIAY_DEP = 13
CAT_PHU_KIEN = 14


def request(url, method="GET", data=None, headers=None, raw_response=False):
    if data is not None and not isinstance(data, (str, bytes)):
        data = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = r.read().decode("utf-8")
            if raw_response:
                return r, body
            return json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"  ✗ HTTP {e.code} {url} → {body[:200]}", file=sys.stderr)
        if raw_response:
            return e, body
        return None


def login_admin():
    """Login admin → return access token from Set-Cookie."""
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
    raise RuntimeError("no token in Set-Cookie")


def variant(sku: str, price: int, qty: int, img: str, attrs: list, sale=None):
    """Variant DTO builder. `attrs` is list of (attrId, attrValId)."""
    v = {
        "sku": sku,
        "price": price,
        "quantity": qty,
        "image": img,
        "attrs": [{"attrId": a, "attrValId": v} for a, v in attrs],
    }
    if sale:
        v["salePrice"] = sale
    return v


def product(name, desc, brand, cat_id, thumb, variants, images=None):
    return {
        "name": name,
        "description": desc,
        "brand": brand,
        "categoryId": cat_id,
        "thumbnail": thumb,
        "images": images or [thumb],
        "activate": True,
        "variants": variants,
    }


def main():
    token = login_admin()
    H = {"Authorization": f"Bearer {token}"}
    print(f"✓ Logged in, token len={len(token)}")

    img_tshirt = "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600"
    img_iphone = "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=600"
    img_laptop = "https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=600"
    img_mba = "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600"
    img_snk = "https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=600"
    img_bag = "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600"
    img_hp = "https://images.unsplash.com/photo-1545127398-14699f92334b?w=600"
    img_jeans = "https://images.unsplash.com/photo-1542272604-787c3835535d?w=600"
    img_dress = "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=600"
    img_watch = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600"

    products = [
        # ─ Áo thun: Size × Color matrix ─
        product(
            "Áo thun cotton premium",
            "Áo thun cotton 100% organic, mềm mịn, thấm hút mồ hôi. Phong cách tối giản dễ phối đồ.",
            "Luxury Mart",
            CAT_QUAN_AO,
            img_tshirt,
            [
                variant("AT-CTP-DEN-S", 189000, 25, img_tshirt,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("AT-CTP-DEN-M", 189000, 40, img_tshirt,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("AT-CTP-DEN-L", 189000, 35, img_tshirt,
                        [(ATTR_SIZE, SIZE["L"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("AT-CTP-TRG-S", 189000, 20, img_tshirt,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Trắng"])]),
                variant("AT-CTP-TRG-M", 189000, 30, img_tshirt,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Trắng"])]),
                variant("AT-CTP-TRG-L", 189000, 28, img_tshirt,
                        [(ATTR_SIZE, SIZE["L"]), (ATTR_COLOR, COLOR["Trắng"])]),
            ],
        ),
        # ─ iPhone: Storage × Color ─
        product(
            "iPhone 16 Pro Max",
            "Chip A18 Pro, camera 48MP, màn hình Super Retina XDR 6.9-inch ProMotion 120Hz. Hộp đầy đủ phụ kiện chính hãng.",
            "Apple",
            CAT_DIEN_THOAI,
            img_iphone,
            [
                variant("IP16PM-DEN-256", 29990000, 12, img_iphone,
                        [(ATTR_STORAGE, STORAGE["256GB"]), (ATTR_COLOR, COLOR["Đen"])],
                        sale=28490000),
                variant("IP16PM-DEN-512", 34990000, 8, img_iphone,
                        [(ATTR_STORAGE, STORAGE["512GB"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("IP16PM-DEN-1TB", 39990000, 5, img_iphone,
                        [(ATTR_STORAGE, STORAGE["1TB"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("IP16PM-TRG-256", 29990000, 10, img_iphone,
                        [(ATTR_STORAGE, STORAGE["256GB"]), (ATTR_COLOR, COLOR["Trắng"])],
                        sale=28490000),
                variant("IP16PM-TRG-512", 34990000, 7, img_iphone,
                        [(ATTR_STORAGE, STORAGE["512GB"]), (ATTR_COLOR, COLOR["Trắng"])]),
            ],
        ),
        # ─ Laptop ROG: RAM × Storage ─
        product(
            "Laptop Gaming Asus ROG Strix G16",
            "RTX 4070, Intel Core i9-13900H, màn 16-inch QHD+ 240Hz, bàn phím Per-Key RGB.",
            "Asus",
            CAT_LAPTOP,
            img_laptop,
            [
                variant("ROG-G16-16-512", 47990000, 8, img_laptop,
                        [(ATTR_RAM, RAM["16GB"]), (ATTR_STORAGE, STORAGE["512GB"])]),
                variant("ROG-G16-16-1TB", 52990000, 6, img_laptop,
                        [(ATTR_RAM, RAM["16GB"]), (ATTR_STORAGE, STORAGE["1TB"])]),
                variant("ROG-G16-32-1TB", 64990000, 3, img_laptop,
                        [(ATTR_RAM, RAM["32GB"]), (ATTR_STORAGE, STORAGE["1TB"])]),
            ],
        ),
        # ─ MacBook Air M3: RAM × Storage ─
        product(
            "MacBook Air M3",
            "Chip Apple M3 mới nhất. Màn hình Liquid Retina 13.6-inch. Pin 18h, mỏng nhẹ 1.24kg.",
            "Apple",
            CAT_LAPTOP,
            img_mba,
            [
                variant("MBA-M3-8-256", 26990000, 10, img_mba,
                        [(ATTR_RAM, RAM["8GB"]), (ATTR_STORAGE, STORAGE["256GB"])],
                        sale=25490000),
                variant("MBA-M3-8-512", 30990000, 8, img_mba,
                        [(ATTR_RAM, RAM["8GB"]), (ATTR_STORAGE, STORAGE["512GB"])]),
                variant("MBA-M3-16-512", 34990000, 5, img_mba,
                        [(ATTR_RAM, RAM["16GB"]), (ATTR_STORAGE, STORAGE["512GB"])]),
            ],
        ),
        # ─ Sneaker Nike: Size × Color ─
        product(
            "Giày sneaker Nike Air Force 1",
            "Mẫu sneaker huyền thoại từ Nike. Chất liệu da bò cao cấp, đế cao su chống trượt.",
            "Nike",
            CAT_GIAY_DEP,
            img_snk,
            [
                variant("NK-AF1-DEN-38", 2890000, 12, img_snk,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Đen"])],
                        sale=2390000),
                variant("NK-AF1-DEN-39", 2890000, 15, img_snk,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Đen"])],
                        sale=2390000),
                variant("NK-AF1-DEN-40", 2890000, 18, img_snk,
                        [(ATTR_SIZE, SIZE["L"]), (ATTR_COLOR, COLOR["Đen"])]),
                variant("NK-AF1-TRG-38", 2890000, 8, img_snk,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Trắng"])]),
                variant("NK-AF1-TRG-39", 2890000, 10, img_snk,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Trắng"])]),
                variant("NK-AF1-TRG-40", 2890000, 14, img_snk,
                        [(ATTR_SIZE, SIZE["L"]), (ATTR_COLOR, COLOR["Trắng"])]),
            ],
        ),
        # ─ Túi xách: Color only ─
        product(
            "Túi xách da Premium",
            "Túi da bò thật, may thủ công, lót da lộn cao cấp. Phù hợp đi làm, dạo phố.",
            "Luxury Mart",
            CAT_PHU_KIEN,
            img_bag,
            [
                variant("TX-PRM-DEN", 1290000, 24, img_bag,
                        [(ATTR_COLOR, COLOR["Đen"])]),
                variant("TX-PRM-XAM", 1290000, 18, img_bag,
                        [(ATTR_COLOR, COLOR["Xám"])],
                        sale=990000),
            ],
        ),
        # ─ Headphone Sony: Color ─
        product(
            "Tai nghe Sony WH-1000XM5",
            "Tai nghe chống ồn flagship. Pin 30h, Bluetooth 5.2, kết nối đa thiết bị, driver 30mm.",
            "Sony",
            CAT_PHU_KIEN,
            img_hp,
            [
                variant("SONY-XM5-DEN", 8990000, 12, img_hp,
                        [(ATTR_COLOR, COLOR["Đen"])],
                        sale=7490000),
                variant("SONY-XM5-TRG", 8990000, 8, img_hp,
                        [(ATTR_COLOR, COLOR["Trắng"])]),
            ],
        ),
        # ─ Jeans: Size only ─
        product(
            "Quần jeans nam slim fit",
            "Vải jeans cao cấp co giãn 4 chiều. Phom slim fit ôm dáng, không gò bó.",
            "Luxury Mart",
            CAT_QUAN_AO,
            img_jeans,
            [
                variant("QJ-SF-29", 499000, 15, img_jeans, [(ATTR_SIZE, SIZE["S"])]),
                variant("QJ-SF-30", 499000, 25, img_jeans, [(ATTR_SIZE, SIZE["M"])]),
                variant("QJ-SF-31", 499000, 20, img_jeans, [(ATTR_SIZE, SIZE["L"])]),
                variant("QJ-SF-32", 499000, 12, img_jeans, [(ATTR_SIZE, SIZE["XL"])]),
            ],
        ),
        # ─ Váy: Size × Color ─
        product(
            "Váy maxi hoa nhí",
            "Váy maxi vải voan mềm, hoa nhí nhỏ nhắn. Form rộng thoải mái cho mùa hè.",
            "Luxury Mart",
            CAT_QUAN_AO,
            img_dress,
            [
                variant("VM-HOA-DOC-S", 690000, 12, img_dress,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Đỏ"])]),
                variant("VM-HOA-DOC-M", 690000, 18, img_dress,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Đỏ"])]),
                variant("VM-HOA-XANH-S", 690000, 10, img_dress,
                        [(ATTR_SIZE, SIZE["S"]), (ATTR_COLOR, COLOR["Xanh"])],
                        sale=590000),
                variant("VM-HOA-XANH-M", 690000, 15, img_dress,
                        [(ATTR_SIZE, SIZE["M"]), (ATTR_COLOR, COLOR["Xanh"])],
                        sale=590000),
            ],
        ),
        # ─ Smart watch: Color ─
        product(
            "Đồng hồ thông minh FitPro Watch",
            "Theo dõi nhịp tim, SpO2, giấc ngủ, GPS. Pin 7 ngày, chống nước 5ATM. AOD luôn bật.",
            "FitPro",
            CAT_PHU_KIEN,
            img_watch,
            [
                variant("FP-WATCH-DEN", 3490000, 20, img_watch,
                        [(ATTR_COLOR, COLOR["Đen"])],
                        sale=2890000),
                variant("FP-WATCH-XAM", 3490000, 15, img_watch,
                        [(ATTR_COLOR, COLOR["Xám"])]),
            ],
        ),
    ]

    print(f"\n📦 Seeding {len(products)} products with rich variant attrs:")
    for p in products:
        body = json.dumps(p).encode()
        req = urllib.request.Request(
            f"{CATALOG}/api/v1/products", data=body, method="POST",
        )
        req.add_header("Content-Type", "application/json")
        req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                resp = json.loads(r.read().decode())
                pid = resp.get("data", resp).get("id", "?")
                variant_count = len(p["variants"])
                total_qty = sum(v["quantity"] for v in p["variants"])
                print(f"  ✓ #{pid:3} {p['name']:40s} ({variant_count} variants, total stock {total_qty})")
        except urllib.error.HTTPError as e:
            body = e.read().decode()[:250]
            print(f"  ✗ {p['name']} → HTTP {e.code}: {body}")

    print("\n✅ Done. Check at:")
    print("   http://localhost:3000/         (storefront)")
    print("   http://localhost:3000/admin/products")


if __name__ == "__main__":
    main()
