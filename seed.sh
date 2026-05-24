#!/usr/bin/env bash
set -e
AUTH="http://localhost:8081"
CATALOG="http://localhost:8083"
VOUCHER="http://localhost:8094"

echo "📦 1. Register users"
curl -sS -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@luxury.vn","password":"Admin@2026","firstName":"Quan","lastName":"Tri"}' > /tmp/r-admin.json
echo "  admin → $(head -c 100 /tmp/r-admin.json)"
curl -sS -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d '{"username":"customer","email":"customer@luxury.vn","password":"Customer@2026","firstName":"Khach","lastName":"Hang"}' > /tmp/r-cust.json
echo "  customer → $(head -c 100 /tmp/r-cust.json)"

echo
echo "📦 2. Promote admin → ROLE_ADMIN"
docker exec hieu-postgres-auth psql -U authuser -d authdb -c "
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username='admin');
INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username='admin' AND r.name='ROLE_ADMIN';
SELECT u.username, r.name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.username='admin';
" 2>&1 | tail -5

echo
echo "📦 3. Login admin (extract token from Set-Cookie)"
HDR=$(curl -sS -i -X POST "$AUTH/api/v1/auth/login" -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"Admin@2026"}')
TOKEN=$(echo "$HDR" | grep -i "Set-Cookie:.*ACCESS_TOKEN" | head -1 | sed -E 's/.*ACCESS_TOKEN=([^;]+).*/\1/')
if [[ -z "$TOKEN" ]]; then echo "❌ no token in Set-Cookie"; echo "$HDR" | head -30; exit 1; fi
echo "  ✓ token len=${#TOKEN}"

H="Authorization: Bearer $TOKEN"
J="Content-Type: application/json"

extract_id() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',d).get('id','') or d.get('id',''))" 2>/dev/null; }

mk_cat() {
  local name="$1" parent="${2:-null}"
  local resp=$(curl -sS -X POST "$CATALOG/api/v1/categories" -H "$H" -H "$J" \
    -d "{\"name\":\"$name\",\"description\":\"$name\",\"parentId\":$parent,\"sortOrder\":0}")
  local id=$(echo "$resp" | extract_id)
  if [[ -z "$id" ]]; then echo "  ✗ $name → $(echo $resp | head -c 200)" >&2; fi
  echo "$id"
}

echo
echo "📦 4. Seed categories"
QA=$(mk_cat "Quần áo")
DT=$(mk_cat "Điện thoại")
LP=$(mk_cat "Laptop")
GD=$(mk_cat "Giày dép")
PK=$(mk_cat "Phụ kiện")
echo "  Parent IDs → QA=$QA DT=$DT LP=$LP GD=$GD PK=$PK"
[[ -n "$QA" ]] && mk_cat "Áo thun" "$QA" > /dev/null && echo "  ✓ Áo thun"
[[ -n "$QA" ]] && mk_cat "Quần jeans" "$QA" > /dev/null && echo "  ✓ Quần jeans"
[[ -n "$DT" ]] && mk_cat "iPhone" "$DT" > /dev/null && echo "  ✓ iPhone"
[[ -n "$DT" ]] && mk_cat "Samsung" "$DT" > /dev/null && echo "  ✓ Samsung"
[[ -n "$LP" ]] && mk_cat "MacBook" "$LP" > /dev/null && echo "  ✓ MacBook"

echo
echo "📦 5. Seed attributes"
mk_attr() {
  local code="$1" name="$2" type="$3"; shift 3
  local vals="[]"
  if [[ $# -gt 0 ]]; then
    vals="["; local first=1
    for v in "$@"; do
      [[ $first -eq 0 ]] && vals="$vals,"; first=0
      local cc=$(echo "$v" | python3 -c "import sys,unicodedata,re;t=unicodedata.normalize('NFD',sys.stdin.read().strip()).encode('ascii','ignore').decode();t=re.sub(r'[^a-zA-Z0-9]+','-',t).lower().strip('-');print(t)")
      vals="$vals{\"code\":\"$cc\",\"val\":\"$v\"}"
    done
    vals="$vals]"
  fi
  local resp=$(curl -sS -X POST "$CATALOG/api/v1/attrs" -H "$H" -H "$J" \
    -d "{\"code\":\"$code\",\"name\":\"$name\",\"type\":\"$type\",\"values\":$vals}")
  local id=$(echo "$resp" | extract_id)
  [[ -z "$id" ]] && echo "  ✗ $name → $(echo $resp | head -c 180)" || echo "  ✓ $name (id=$id)"
}
mk_attr "size" "Kích cỡ" "SELECT" "S" "M" "L" "XL" "XXL"
mk_attr "color" "Màu sắc" "SELECT" "Đen" "Trắng" "Đỏ" "Xanh" "Xám"
mk_attr "storage" "Bộ nhớ" "SELECT" "64GB" "128GB" "256GB" "512GB" "1TB"
mk_attr "ram" "RAM" "SELECT" "4GB" "8GB" "16GB" "32GB"
mk_attr "material" "Chất liệu" "TEXT"

echo
echo "📦 6. Seed products"
mk_p() {
  local body="$1"
  local resp=$(curl -sS -X POST "$CATALOG/api/v1/products" -H "$H" -H "$J" -d "$body")
  local name=$(echo "$body" | python3 -c "import sys,json; print(json.load(sys.stdin)['name'])" 2>/dev/null)
  local id=$(echo "$resp" | extract_id)
  [[ -z "$id" ]] && echo "  ✗ $name → $(echo $resp | head -c 250)" || echo "  ✓ $name (id=$id)"
}

IMG_TSHIRT="https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600"
IMG_IP="https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=600"
IMG_LAPTOP="https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=600"
IMG_SNK="https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=600"
IMG_BAG="https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600"
IMG_HP="https://images.unsplash.com/photo-1545127398-14699f92334b?w=600"
IMG_JEANS="https://images.unsplash.com/photo-1542272604-787c3835535d?w=600"
IMG_MBA="https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600"

mk_p "{\"name\":\"Áo thun cotton Luxury Mart\",\"description\":\"Áo thun cotton 100% organic, mềm mịn, thấm hút mồ hôi. Phong cách tối giản, dễ phối đồ.\",\"brand\":\"Luxury Mart\",\"categoryId\":$QA,\"thumbnail\":\"$IMG_TSHIRT\",\"images\":[\"$IMG_TSHIRT\"],\"activate\":true,\"variants\":[{\"sku\":\"AT-CT-S\",\"price\":189000,\"quantity\":50,\"image\":\"$IMG_TSHIRT\",\"attrs\":[]},{\"sku\":\"AT-CT-M\",\"price\":189000,\"quantity\":80,\"image\":\"$IMG_TSHIRT\",\"attrs\":[]},{\"sku\":\"AT-CT-L\",\"price\":189000,\"quantity\":70,\"image\":\"$IMG_TSHIRT\",\"attrs\":[]}]}"

mk_p "{\"name\":\"iPhone 16 Pro Max\",\"description\":\"Chip A18 Pro, camera 48MP, màn hình ProMotion 120Hz. Hộp đầy đủ phụ kiện chính hãng.\",\"brand\":\"Apple\",\"categoryId\":$DT,\"thumbnail\":\"$IMG_IP\",\"activate\":true,\"variants\":[{\"sku\":\"IP16PM-256\",\"price\":29990000,\"salePrice\":28490000,\"quantity\":25,\"image\":\"$IMG_IP\",\"attrs\":[]},{\"sku\":\"IP16PM-512\",\"price\":34990000,\"quantity\":18,\"image\":\"$IMG_IP\",\"attrs\":[]},{\"sku\":\"IP16PM-1TB\",\"price\":39990000,\"quantity\":8,\"image\":\"$IMG_IP\",\"attrs\":[]}]}"

mk_p "{\"name\":\"Laptop Gaming Asus ROG Strix\",\"description\":\"RTX 4070, Intel Core i9, 16GB DDR5, 1TB NVMe SSD. Màn hình 16-inch QHD+ 240Hz.\",\"brand\":\"Asus\",\"categoryId\":$LP,\"thumbnail\":\"$IMG_LAPTOP\",\"activate\":true,\"variants\":[{\"sku\":\"ROG-STRIX-1\",\"price\":52990000,\"quantity\":12,\"image\":\"$IMG_LAPTOP\",\"attrs\":[]}]}"

mk_p "{\"name\":\"Giày sneaker Nike Air Force 1\",\"description\":\"Mẫu sneaker huyền thoại từ Nike. Chất liệu da cao cấp, đế cao su chống trượt.\",\"brand\":\"Nike\",\"categoryId\":$GD,\"thumbnail\":\"$IMG_SNK\",\"activate\":true,\"variants\":[{\"sku\":\"NK-AF1-38\",\"price\":2890000,\"salePrice\":2390000,\"quantity\":30,\"image\":\"$IMG_SNK\",\"attrs\":[]},{\"sku\":\"NK-AF1-39\",\"price\":2890000,\"salePrice\":2390000,\"quantity\":40,\"image\":\"$IMG_SNK\",\"attrs\":[]},{\"sku\":\"NK-AF1-40\",\"price\":2890000,\"quantity\":35,\"image\":\"$IMG_SNK\",\"attrs\":[]},{\"sku\":\"NK-AF1-41\",\"price\":2890000,\"quantity\":28,\"image\":\"$IMG_SNK\",\"attrs\":[]},{\"sku\":\"NK-AF1-42\",\"price\":2890000,\"quantity\":22,\"image\":\"$IMG_SNK\",\"attrs\":[]}]}"

mk_p "{\"name\":\"Túi xách da Premium\",\"description\":\"Túi da bò thật, may thủ công, lót da lộn cao cấp. Phù hợp đi làm dạo phố.\",\"brand\":\"Luxury Mart\",\"categoryId\":$PK,\"thumbnail\":\"$IMG_BAG\",\"activate\":true,\"variants\":[{\"sku\":\"TX-PRM-BR\",\"price\":1290000,\"salePrice\":990000,\"quantity\":18,\"image\":\"$IMG_BAG\",\"attrs\":[]},{\"sku\":\"TX-PRM-BLK\",\"price\":1290000,\"quantity\":24,\"image\":\"$IMG_BAG\",\"attrs\":[]}]}"

mk_p "{\"name\":\"Tai nghe Sony WH-1000XM5\",\"description\":\"Tai nghe chống ồn flagship của Sony. Pin 30h, Bluetooth 5.2, kết nối đa thiết bị.\",\"brand\":\"Sony\",\"categoryId\":$PK,\"thumbnail\":\"$IMG_HP\",\"activate\":true,\"variants\":[{\"sku\":\"SONY-XM5-BK\",\"price\":8990000,\"salePrice\":7490000,\"quantity\":15,\"image\":\"$IMG_HP\",\"attrs\":[]},{\"sku\":\"SONY-XM5-SL\",\"price\":8990000,\"quantity\":10,\"image\":\"$IMG_HP\",\"attrs\":[]}]}"

mk_p "{\"name\":\"Quần jeans nam slim fit\",\"description\":\"Vải jeans cao cấp co giãn 4 chiều. Phom slim fit tôn dáng.\",\"brand\":\"Luxury Mart\",\"categoryId\":$QA,\"thumbnail\":\"$IMG_JEANS\",\"activate\":true,\"variants\":[{\"sku\":\"QJ-SF-29\",\"price\":499000,\"quantity\":20,\"image\":\"$IMG_JEANS\",\"attrs\":[]},{\"sku\":\"QJ-SF-30\",\"price\":499000,\"quantity\":30,\"image\":\"$IMG_JEANS\",\"attrs\":[]},{\"sku\":\"QJ-SF-31\",\"price\":499000,\"quantity\":25,\"image\":\"$IMG_JEANS\",\"attrs\":[]}]}"

mk_p "{\"name\":\"MacBook Air M3\",\"description\":\"Chip M3 mới nhất. Màn 13.6-inch Liquid Retina. Pin 18h, mỏng nhẹ 1.24kg.\",\"brand\":\"Apple\",\"categoryId\":$LP,\"thumbnail\":\"$IMG_MBA\",\"activate\":true,\"variants\":[{\"sku\":\"MBA-M3-256\",\"price\":27990000,\"salePrice\":26490000,\"quantity\":15,\"image\":\"$IMG_MBA\",\"attrs\":[]},{\"sku\":\"MBA-M3-512\",\"price\":31990000,\"quantity\":12,\"image\":\"$IMG_MBA\",\"attrs\":[]}]}"

echo
echo "📦 7. Seed vouchers"
mk_v() {
  local resp=$(curl -sS -X POST "$VOUCHER/api/v1/vouchers" -H "$H" -H "$J" -d "$1")
  local code=$(echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
  local id=$(echo "$resp" | extract_id)
  [[ -z "$id" ]] && echo "  ✗ $code → $(echo $resp | head -c 150)" || echo "  ✓ $code"
}
mk_v '{"code":"WELCOME10","type":"PERCENTAGE","discountValue":10,"minOrderAmount":200000,"usageLimit":1000,"description":"Giảm 10% đơn đầu"}'
mk_v '{"code":"SUMMER50K","type":"FIXED_AMOUNT","discountValue":50000,"minOrderAmount":500000,"usageLimit":500,"description":"Giảm 50K từ 500K"}'
mk_v '{"code":"VIP20","type":"PERCENTAGE","discountValue":20,"minOrderAmount":1000000,"maxDiscountAmount":500000,"usageLimit":200,"description":"Giảm 20% đơn VIP"}'

echo
echo "✅ Seed done!"
echo "  Admin    : admin    / Admin@2026"
echo "  Customer : customer / Customer@2026"
