from __future__ import annotations

from io import BytesIO

from PIL import Image


def jpeg_bytes(color: tuple[int, int, int] = (40, 70, 110)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (32, 24), color).save(buffer, format="JPEG")
    return buffer.getvalue()


def dng_bytes(marker: bytes = b"rawjudge") -> bytes:
    return b"II*\x00" + marker + b"\x00" * 64


def register(client, name: str, handle: str | None = None):
    response = client.post(
        "/v1/auth/anonymous",
        json={"display_name": name, "handle": handle},
    )
    assert response.status_code == 201, response.text
    payload = response.json()
    return payload["user"], {"Authorization": f"Bearer {payload['access_token']}"}


def upload_work(
    client,
    headers,
    title: str,
    *,
    raw: bool = True,
    preview_price: float = 0.0,
    raw_price: float = 0.0,
):
    files = {"image": (f"{title}.jpg", jpeg_bytes(), "image/jpeg")}
    if raw:
        files["raw"] = (f"{title}.dng", dng_bytes(title.encode()), "application/octet-stream")
    return client.post(
        "/v1/works",
        headers=headers,
        data={
            "title": title,
            "description": "自然光，说明构图、色彩和后期边界。",
            "allow_preview": "true",
            "allow_raw": "true",
            "preview_price": str(preview_price),
            "raw_price": str(raw_price),
        },
        files=files,
    )


def test_health_ready_and_honest_seed_data(client):
    assert client.get("/health").json()["status"] == "ok"
    assert client.get("/ready").json()["status"] == "ready"
    works = client.get("/v1/works").json()
    assert len(works) >= 4
    assert all(work["official_sample"] for work in works[:4])
    assert all(work["favorites"] == 0 for work in works[:4])
    assert all(work["downloads"] == 0 for work in works[:4])
    assert all(not review["score_counted"] for work in works[:4] for review in work["reviews"])


def test_anonymous_session_and_profile_update(client):
    user, headers = register(client, "摄影者", "@photo-user")
    assert user["handle"] == "@photo-user"
    me = client.get("/v1/me", headers=headers)
    assert me.status_code == 200
    updated = client.patch(
        "/v1/me",
        headers=headers,
        json={
            "display_name": "新名字",
            "handle": "new-handle",
            "external_url": "https://example.com/profile",
        },
    )
    assert updated.status_code == 200, updated.text
    assert updated.json()["handle"] == "@new-handle"
    assert updated.json()["external_url"].startswith("https://example.com/profile")


def test_upload_requires_auth_and_server_owns_identity(client):
    denied = client.post(
        "/v1/works",
        data={"title": "No session"},
        files={"image": ("photo.jpg", jpeg_bytes(), "image/jpeg")},
    )
    assert denied.status_code == 401

    user, headers = register(client, "真实作者", "@real-author")
    response = upload_work(client, headers, "Rain station")
    assert response.status_code == 201, response.text
    work = response.json()
    assert work["owner_id"] == user["id"]
    assert work["author_name"] == "真实作者"
    assert work["handle"] == "@real-author"
    assert work["raw_verified"] is True
    assert work["moderation_status"] == "reviewing"
    assert work["is_owner"] is True
    assert work["can_download_original"] is True
    assert work["can_download_raw"] is True


def test_invalid_or_missing_raw_is_private_to_owner(client):
    _, headers = register(client, "No Raw Owner")
    response = upload_work(client, headers, "No RAW", raw=False)
    assert response.status_code == 201, response.text
    work = response.json()
    assert work["moderation_status"] == "needs_raw"
    assert work["partition"] == "archive"
    assert "raw_missing_or_invalid" in work["risk_labels"]
    assert client.get(f"/v1/works/{work['id']}").status_code == 404
    assert client.get(f"/v1/works/{work['id']}", headers=headers).status_code == 200


def test_public_preview_is_sanitized_and_original_is_private(client):
    _, owner_headers = register(client, "Preview Owner")
    work = upload_work(client, owner_headers, "Preview Test").json()
    preview = client.get(work["image_url"])
    assert preview.status_code == 200
    assert preview.headers["content-type"].startswith("image/jpeg")
    assert preview.content.startswith(b"\xff\xd8")

    _, viewer_headers = register(client, "Viewer")
    denied = client.get(f"/v1/works/{work['id']}/original", headers=viewer_headers)
    assert denied.status_code == 403
    owner = client.get(f"/v1/works/{work['id']}/original", headers=owner_headers)
    assert owner.status_code == 200


def test_comment_quality_self_review_and_weighted_score(client):
    owner, owner_headers = register(client, "Review Owner")
    work = upload_work(client, owner_headers, "Review Target").json()
    before = work["score"]
    self_review = client.post(
        f"/v1/works/{work['id']}/reviews",
        headers=owner_headers,
        json={
            "body": "构图和色彩都很好，建议继续保持这种主体安排和曝光策略。",
            "score": 100,
        },
    )
    assert self_review.status_code == 201
    assert self_review.json()["score_counted"] is False
    assert client.get(f"/v1/works/{work['id']}", headers=owner_headers).json()["score"] == before

    reviewer, reviewer_headers = register(client, "Reviewer")
    generic = client.post(
        f"/v1/works/{work['id']}/reviews",
        headers=reviewer_headers,
        json={"body": "好看", "score": 100},
    )
    assert generic.status_code == 201
    assert generic.json()["folded"] is True
    assert generic.json()["score_counted"] is False

    detailed = client.post(
        f"/v1/works/{work['id']}/reviews",
        headers=reviewer_headers,
        json={
            "body": "主体与右侧路牌重叠，建议降低机位并减少背景干扰；暗部曝光仍有 RAW 调整空间。",
            "score": 82,
        },
    )
    assert detailed.status_code == 201
    assert detailed.json()["reviewer_user_id"] == reviewer["id"]
    assert detailed.json()["metrics"]["quality"] > generic.json()["metrics"]["quality"]
    assert detailed.json()["score_counted"] is True


def test_favorite_is_idempotent_and_bound_to_session(client):
    _, owner_headers = register(client, "Favorite Owner")
    work = upload_work(client, owner_headers, "Favorite Work").json()
    _, user_headers = register(client, "Favorite User")
    first = client.post(
        f"/v1/works/{work['id']}/favorite",
        headers=user_headers,
        json={"favorite": True},
    )
    second = client.post(
        f"/v1/works/{work['id']}/favorite",
        headers=user_headers,
        json={"favorite": True},
    )
    assert first.json()["favorites"] == 1
    assert second.json()["favorites"] == 1
    assert second.json()["is_favorite"] is True
    removed = client.post(
        f"/v1/works/{work['id']}/favorite",
        headers=user_headers,
        json={"favorite": False},
    )
    assert removed.json()["favorites"] == 0
    assert removed.json()["is_favorite"] is False


def test_blind_review_hides_identity_and_rejects_owner_vote(client):
    owner_a, headers_a = register(client, "Owner A")
    owner_b, headers_b = register(client, "Owner B")
    work_a = upload_work(client, headers_a, "Blind A").json()
    work_b = upload_work(client, headers_b, "Blind B").json()
    _, voter_headers = register(client, "Blind Voter")
    pair_response = client.get("/v1/blind/pair?sequence=0", headers=voter_headers)
    assert pair_response.status_code == 200, pair_response.text
    pair = pair_response.json()
    assert set(pair["left"]) == {"id", "image_url", "raw_verified", "confidence"}
    vote_payload = {
        "left_work_id": pair["left"]["id"],
        "right_work_id": pair["right"]["id"],
        "winner_work_id": pair["left"]["id"],
    }
    first = client.post("/v1/blind/vote", headers=voter_headers, json=vote_payload)
    second = client.post("/v1/blind/vote", headers=voter_headers, json=vote_payload)
    assert first.status_code == 200
    assert "weight 0.00" in second.json()["message"]

    owner_headers = headers_a if owner_a["id"] in {work_a["owner_id"], work_b["owner_id"]} else headers_b
    owner_vote = client.post("/v1/blind/vote", headers=owner_headers, json=vote_payload)
    assert owner_vote.status_code == 403


def test_report_duplicate_and_appeal_owner_permissions(client):
    _, owner_headers = register(client, "Appeal Owner")
    work = upload_work(client, owner_headers, "Appeal Work", raw=False).json()
    _, other_headers = register(client, "Other User")
    denied = client.post(
        f"/v1/works/{work['id']}/appeals",
        headers=other_headers,
        json={"reason": "I am not the owner"},
    )
    assert denied.status_code == 403
    appeal = client.post(
        f"/v1/works/{work['id']}/appeals",
        headers=owner_headers,
        json={"reason": "请复核 RAW 文件上传结果"},
    )
    assert appeal.status_code == 201
    duplicate_appeal = client.post(
        f"/v1/works/{work['id']}/appeals",
        headers=owner_headers,
        json={"reason": "重复申诉"},
    )
    assert duplicate_appeal.status_code == 409

    public_work = client.get("/v1/works").json()[0]
    report = client.post(
        f"/v1/works/{public_work['id']}/reports",
        headers=other_headers,
        json={"reason": "测试举报原因"},
    )
    duplicate = client.post(
        f"/v1/works/{public_work['id']}/reports",
        headers=other_headers,
        json={"reason": "再次举报"},
    )
    assert report.status_code == 201
    assert duplicate.status_code == 409


def test_free_license_grants_access_and_paid_license_never_fakes_payment(client):
    _, owner_headers = register(client, "License Owner")
    free_work = upload_work(client, owner_headers, "Free RAW", raw_price=0).json()
    paid_work = upload_work(client, owner_headers, "Paid RAW", raw_price=2).json()
    _, buyer_headers = register(client, "License Buyer")

    denied = client.get(f"/v1/works/{free_work['id']}/raw", headers=buyer_headers)
    assert denied.status_code == 403
    grant = client.post(
        f"/v1/works/{free_work['id']}/licenses/raw_study",
        headers=buyer_headers,
    )
    assert grant.status_code == 201, grant.text
    assert grant.json()["granted"] is True
    raw_response = client.get(f"/v1/works/{free_work['id']}/raw", headers=buyer_headers)
    assert raw_response.status_code == 200
    assert raw_response.content.startswith(b"II*\x00")
    duplicate = client.post(
        f"/v1/works/{free_work['id']}/licenses/raw_study",
        headers=buyer_headers,
    )
    assert duplicate.status_code == 201
    assert duplicate.json()["id"] == grant.json()["id"]

    paid = client.post(
        f"/v1/works/{paid_work['id']}/licenses/raw_study",
        headers=buyer_headers,
    )
    assert paid.status_code == 402


def test_invalid_image_and_fake_raw_are_rejected_or_archived(client):
    _, headers = register(client, "File Validator")
    bad_image = client.post(
        "/v1/works",
        headers=headers,
        data={"title": "Bad image"},
        files={"image": ("bad.jpg", b"not-an-image", "image/jpeg")},
    )
    assert bad_image.status_code == 400

    fake_raw = client.post(
        "/v1/works",
        headers=headers,
        data={"title": "Fake RAW", "allow_raw": "true"},
        files={
            "image": ("valid.jpg", jpeg_bytes(), "image/jpeg"),
            "raw": ("fake.dng", b"this-is-not-tiff", "application/octet-stream"),
        },
    )
    assert fake_raw.status_code == 201
    assert fake_raw.json()["raw_verified"] is False
    assert fake_raw.json()["moderation_status"] == "needs_raw"
