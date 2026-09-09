import json
import urllib.request
import urllib.error
import time

BASE_URL = "http://localhost:8080/api/v1"

def request(path, method="GET", data=None, token=None):
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except Exception:
            return e.code, {"error": err_body}

def run_tests():
    print("=== STARTING FULL E2E SMOKE TEST FOR PHONGTROXANH.VN BACKEND ===")
    ts = int(time.time())

    # 1. Register Tenant
    tenant_email = f"tenant_{ts}@example.com"
    print(f"\n[1] Registering Tenant: {tenant_email}...")
    status, res = request("/auth/register", "POST", {
        "email": tenant_email,
        "phoneNumber": f"091{ts % 10000000:07d}",
        "password": "Password123@",
        "fullName": "Nguyen Van Tenant",
        "role": "TENANT"
    })
    assert status == 201, f"Tenant register failed: {res}"
    tenant_token = res["data"]["accessToken"]
    print("  -> Tenant registered successfully! Token acquired.")

    # 2. Register Landlord
    landlord_email = f"landlord_{ts}@example.com"
    print(f"\n[2] Registering Landlord: {landlord_email}...")
    status, res = request("/auth/register", "POST", {
        "email": landlord_email,
        "phoneNumber": f"098{ts % 10000000:07d}",
        "password": "Password123@",
        "fullName": "Tran Van Landlord",
        "role": "LANDLORD"
    })
    assert status == 201, f"Landlord register failed: {res}"
    landlord_token = res["data"]["accessToken"]
    print("  -> Landlord registered successfully! Token acquired.")

    # 3. Tenant Profile
    print("\n[3] Tenant fetching own profile...")
    status, res = request("/users/me", "GET", token=tenant_token)
    assert status == 200, f"Get profile failed: {res}"
    print(f"  -> Profile verified: {res['data']['fullName']}, TrustScore: {res['data']['trustScore']}")

    # 4. Tenant Update Matching Profile
    print("\n[4] Tenant updating Roommate Matching Profile...")
    status, res = request("/users/me/matching-profile", "PUT", {
        "budgetMin": 2000000,
        "budgetMax": 3500000,
        "preferredDistricts": ["Bình Thạnh", "Quận 1"],
        "earlySleeper": True,
        "isNeat": True,
        "allowGuests": False,
        "nonSmoking": True,
        "noiseTolerance": 30,
        "interests": ["GYM", "TECH", "READING"]
    }, token=tenant_token)
    assert status == 200, f"Update matching profile failed: {res}"
    print("  -> Roommate matching profile updated.")

    # 5. Landlord creates room
    print("\n[5] Landlord creating new room...")
    status, res = request("/rooms", "POST", {
        "title": "Phòng trọ cao cấp gần ĐH HUTECH Bình Thạnh",
        "description": "Phòng đầy đủ tiện nghi điều hòa nóng lạnh ban công thoáng mát",
        "roomType": "PHONG_KHEP_KIN",
        "price": 3500000,
        "depositAmount": 3500000,
        "areaSqm": 25.0,
        "district": "Bình Thạnh",
        "ward": "Phường 25",
        "addressStreet": "475A Điện Biên Phủ",
        "latitude": 10.8018,
        "longitude": 106.7145,
        "waterCost": 100000,
        "electricityCost": 3800,
        "hasWifi": True,
        "hasAirConditioner": True,
        "hasWaterHeater": True,
        "amenities": ["WIFI", "AIR_CONDITIONER", "WATER_HEATER", "PARKING"]
    }, token=landlord_token)
    assert status == 201, f"Create room failed: {res}"
    room_id = res["data"]["id"]
    print(f"  -> Room created with ID: {room_id}")

    # 6. Search rooms public
    print("\n[6] Searching rooms publicly...")
    district_param = urllib.parse.quote("Bình Thạnh")
    status, res = request(f"/rooms?district={district_param}", "GET")
    assert status == 200, f"Search rooms failed: {res}"
    print(f"  -> Search success! Found {len(res['data']['content'])} room(s).")

    # 7. Tenant creates rental request
    print("\n[7] Tenant requesting rental contract...")
    status, res = request("/rentals", "POST", {
        "roomId": room_id,
        "startDate": "2026-09-10",
        "endDate": "2027-09-10",
        "message": "Tôi muốn thuê phòng dài hạn 1 năm"
    }, token=tenant_token)
    assert status == 201, f"Create rental failed: {res}"
    rental_id = res["data"]["id"]
    print(f"  -> Rental contract created with ID: {rental_id}, Status: {res['data']['status']}")

    # 8. Tenant generates check-in QR
    print("\n[8] Tenant generating Check-in QR code...")
    status, res = request(f"/rentals/{rental_id}/check-in-qr", "GET", token=tenant_token)
    assert status == 200, f"Generate QR failed: {res}"
    qr_payload = res["data"]["qrCodePayload"]
    print(f"  -> Check-in QR generated: {qr_payload}, Expires: {res['data']['expiresAt']}")

    # 9. Landlord verifies check-in QR
    print("\n[9] Landlord scanning & verifying Check-in QR...")
    status, res = request(f"/rentals/{rental_id}/check-in", "POST", {
        "checkInCode": qr_payload
    }, token=landlord_token)
    assert status == 200, f"Verify check-in failed: {res}"
    assert res["data"]["status"] == "CHECKED_IN", f"Expected CHECKED_IN status: {res}"
    print("  -> Check-in VERIFIED! Rental contract is now CHECKED_IN.")

    # 10. Tenant submits review
    print("\n[10] Tenant submitting review for the room & landlord...")
    status, res = request("/reviews", "POST", {
        "rentalId": rental_id,
        "rating": 5,
        "cleanlinessRating": 5,
        "accuracyRating": 5,
        "communicationRating": 5,
        "comment": "Phòng rất đẹp, chủ trọ thân thiện nhiệt tình!"
    }, token=tenant_token)
    assert status == 201, f"Submit review failed: {res}"
    print("  -> Review submitted successfully!")

    # 11. Public reviews for room
    print("\n[11] Fetching public reviews for room...")
    status, res = request(f"/reviews/rooms/{room_id}", "GET")
    assert status == 200, f"Get room reviews failed: {res}"
    print(f"  -> Room has {len(res['data'])} review(s). Top rating: {res['data'][0]['rating']} stars")

    # 12. Monetization plans
    print("\n[12] Checking package plans...")
    status, res = request("/monetization/plans", "GET")
    assert status == 200, f"Get plans failed: {res}"
    print(f"  -> {len(res['data'])} package plans available.")

    # 13. Location Autocomplete
    print("\n[13] Testing Location Autocomplete...")
    loc_param = urllib.parse.quote("Điện Biên Phủ Bình Thạnh")
    status, res = request(f"/locations/autocomplete?input={loc_param}", "GET")
    assert status == 200, f"Autocomplete failed: {res}"
    print(f"  -> Found {len(res['data'])} location suggestions.")

    # 14. Roommate Matching Feed
    print("\n[14] Tenant checking Roommate Matching Feed...")
    status, res = request("/matching/feed", "GET", token=tenant_token)
    assert status == 200, f"Matching feed failed: {res}"
    print(f"  -> Matching feed returned {len(res['data'])} compatible profile(s).")

    print("\n==================================================================")
    print(">>> ALL 14 E2E FLOWS PASSED WITH 100% SUCCESS! BACKEND IS ROBUST! <<<")
    print("==================================================================")

if __name__ == "__main__":
    run_tests()
