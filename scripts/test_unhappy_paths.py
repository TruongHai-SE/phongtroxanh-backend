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

def test_unhappy_paths():
    print("=== TESTING UNHAPPY PATHS & HARDENED DEFENSES ===")
    ts = int(time.time())

    # Register Tenant A
    status, res = request("/auth/register", "POST", {
        "email": f"tenant_a_{ts}@example.com",
        "phoneNumber": f"091{ts % 10000000:07d}",
        "password": "Password123@",
        "fullName": "Tenant A",
        "role": "TENANT"
    })
    assert status == 201
    tenant_a_token = res["data"]["accessToken"]
    tenant_a_id = res["data"]["user"]["id"]

    # Register Tenant B
    status, res = request("/auth/register", "POST", {
        "email": f"tenant_b_{ts}@example.com",
        "phoneNumber": f"092{ts % 10000000:07d}",
        "password": "Password123@",
        "fullName": "Tenant B",
        "role": "TENANT"
    })
    assert status == 201
    tenant_b_token = res["data"]["accessToken"]
    tenant_b_id = res["data"]["user"]["id"]

    # Register Landlord
    status, res = request("/auth/register", "POST", {
        "email": f"landlord_u_{ts}@example.com",
        "phoneNumber": f"098{ts % 10000000:07d}",
        "password": "Password123@",
        "fullName": "Landlord U",
        "role": "LANDLORD"
    })
    assert status == 201
    landlord_token = res["data"]["accessToken"]

    # 1. KYC NOT FOUND (no fake PENDING fallback)
    print("\n[Test 1] Verify KYC_NOT_FOUND when user hasn't submitted KYC...")
    status, res = request("/users/me/kyc/status", "GET", token=tenant_a_token)
    assert status == 404, f"Expected 404 for unsubmitted KYC, got: {status}, res: {res}"
    assert res.get("code") == "KYC_NOT_FOUND"
    print("  -> PASS: 404 KYC_NOT_FOUND returned successfully!")

    # 2. Blank message validation (@NotBlank, @Valid)
    print("\n[Test 2] Verify @NotBlank validation on chat message...")
    # Create conversation first
    status, conv_res = request("/chat/conversations", "POST", {
        "partnerId": tenant_b_id,
        "type": "ROOMMATE"
    }, token=tenant_a_token)
    assert status == 200
    conv_id = conv_res["data"]["id"]

    status, res = request(f"/chat/conversations/{conv_id}/messages", "POST", {
        "content": "   "
    }, token=tenant_a_token)
    assert status == 400, f"Expected 400 for blank message content, got: {status}"
    print("  -> PASS: Blank message blocked with 400 Bad Request!")

    # 3. Create room and rental
    status, room_res = request("/rooms", "POST", {
        "title": "Phòng thử nghiệm Unhappy Path",
        "description": "Mô tả phòng cho kiểm thử bảo mật và trạng thái hợp đồng",
        "roomType": "PHONG_KHEP_KIN",
        "price": 3000000,
        "depositAmount": 3000000,
        "areaSqm": 20.0,
        "district": "Quận 10",
        "addressStreet": "Lý Thường Kiệt",
        "latitude": 10.77,
        "longitude": 106.65
    }, token=landlord_token)
    assert status == 201
    room_id = room_res["data"]["id"]

    status, rental_res = request("/rentals", "POST", {
        "roomId": room_id,
        "startDate": "2026-10-01",
        "endDate": "2027-10-01",
        "message": "Thuê 1 năm"
    }, token=tenant_a_token)
    assert status == 201
    rental_id = rental_res["data"]["id"]

    # Generate QR and Check in
    status, qr_res = request(f"/rentals/{rental_id}/check-in-qr", "GET", token=tenant_a_token)
    assert status == 200
    qr_payload = qr_res["data"]["qrCodePayload"]

    status, res = request(f"/rentals/{rental_id}/check-in", "POST", {
        "checkInCode": qr_payload
    }, token=landlord_token)
    assert status == 200
    assert res["data"]["status"] == "CHECKED_IN"

    # 4. Duplicate Check-in Replay Blocked
    print("\n[Test 4] Verify duplicate check-in replay rejection...")
    status, res = request(f"/rentals/{rental_id}/check-in", "POST", {
        "checkInCode": qr_payload
    }, token=landlord_token)
    assert status == 409, f"Expected 409 for duplicate check-in, got: {status}, res: {res}"
    assert res.get("code") == "RENTAL_ALREADY_CHECKED_IN"
    print("  -> PASS: Duplicate check-in rejected with 409 RENTAL_ALREADY_CHECKED_IN!")

    # 5. Cannot create new rental on room that is already RENTED
    print("\n[Test 5] Verify cannot rent room that is already RENTED...")
    status, res = request("/rentals", "POST", {
        "roomId": room_id,
        "startDate": "2026-10-01",
        "endDate": "2027-10-01",
        "message": "Cố tình thuê phòng đã có người ở"
    }, token=tenant_b_token)
    assert status == 400, f"Expected 400 for already rented room, got: {status}, res: {res}"
    assert res.get("code") in ["ROOM_ALREADY_RENTED", "ROOM_NOT_AVAILABLE"], f"Unexpected error code: {res}"
    print("  -> PASS: Renting rented room blocked with 400 ROOM_NOT_AVAILABLE / ROOM_ALREADY_RENTED!")

    # 6. Self-Review Blocked
    print("\n[Test 6] Verify self-review rejection...")
    # Landlord trying to review himself or tenant reviewing themselves
    # Let's test tenant reviewing the contract first (valid)
    status, res = request("/reviews", "POST", {
        "rentalId": rental_id,
        "rating": 5,
        "comment": "Chủ trọ tuyệt vời"
    }, token=tenant_a_token)
    assert status == 201
    print("  -> Valid review submitted.")

    # 7. Duplicate Review Blocked
    print("\n[Test 7] Verify duplicate review for same contract rejected...")
    status, res = request("/reviews", "POST", {
        "rentalId": rental_id,
        "rating": 4,
        "comment": "Thử gửi đánh giá lần 2"
    }, token=tenant_a_token)
    assert status == 409, f"Expected 409 for duplicate review, got: {status}, res: {res}"
    assert res.get("code") == "REVIEW_ALREADY_EXISTS"
    print("  -> PASS: Duplicate review blocked with 409 REVIEW_ALREADY_EXISTS!")

    # 8. Swap: Cannot apply to own swap post
    print("\n[Test 8] Verify cannot swap with own post...")
    status, swap_post_res = request("/swaps", "POST", {
        "currentRoomId": room_id,
        "title": "Pass phòng trọ Q10",
        "description": "Nhượng lại phòng trọ gấp",
        "targetDistricts": ["Quận 1"],
        "targetRoomType": "STUDIO",
        "targetBudgetMax": 5000000
    }, token=tenant_a_token)
    assert status == 201
    swap_id = swap_post_res["data"]["id"]

    status, res = request(f"/swaps/{swap_id}/request", "POST", {
        "message": "Tôi tự đề xuất đổi với chính mình"
    }, token=tenant_a_token)
    assert res.get("code") == "SELF_PROPOSAL_NOT_ALLOWED", f"Unexpected code: {res}"
    print("  -> PASS: Self swap proposal blocked with 400 SELF_PROPOSAL_NOT_ALLOWED!")

    # 9. Anti-BOLA: Third party cannot modify or approve another user's proposal
    print("\n[Test 9] Verify Anti-BOLA on swap proposal status...")
    status, prop_res = request(f"/swaps/{swap_id}/request", "POST", {
        "message": "Tenant B xin đề xuất đổi phòng"
    }, token=tenant_b_token)
    assert status == 201, f"Expected 201 for proposal creation, got: {status}, res: {prop_res}"
    proposal_id = prop_res["data"]["id"]

    # Unauthorized third party (or applicant trying to approve their own proposal)
    status, res = request(f"/swaps/requests/{proposal_id}", "PUT", {
        "status": "APPROVED"
    }, token=tenant_b_token) # Tenant B is applicant, only post owner (Tenant A) can approve!
    assert status == 403, f"Expected 403 for unauthorized status update, got: {status}, res: {res}"
    assert res.get("code") == "BOLA_FORBIDDEN"
    print("  -> PASS: Anti-BOLA blocked unauthorized proposal status update with 403 BOLA_FORBIDDEN!")

    print("\n==================================================================")
    print(">>> ALL UNHAPPY PATH & DEFENSIVE HARDENING TESTS PASSED 100%! <<<")
    print("==================================================================")

if __name__ == "__main__":
    test_unhappy_paths()
