def test_health_and_seeded_samples(client):
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json()["status"] == "ok"

    works = client.get("/v1/works").json()
    assert len(works) >= 4
    assert all(work["official_sample"] for work in works[:4])


def test_upload_requires_raw(client):
    response = client.post(
        "/v1/works",
        data={"title": "No RAW", "description": "street photo", "author_name": "Tester", "handle": "@tester"},
        files={"image": ("street.jpg", b"jpeg-bytes", "image/jpeg")},
    )
    assert response.status_code == 201, response.text
    work = response.json()
    assert work["moderation_status"] == "needs_raw"
    assert work["partition"] == "archive"
    assert "raw_missing_or_unsupported" in work["risk_labels"]


def test_upload_with_raw_enters_review(client):
    response = client.post(
        "/v1/works",
        data={
            "title": "Rain station",
            "description": "natural light and restrained color",
            "author_name": "Tester",
            "handle": "@tester",
            "allow_raw": "true",
        },
        files={
            "image": ("rain.jpg", b"jpeg-content", "image/jpeg"),
            "raw": ("rain.dng", b"raw-content", "application/octet-stream"),
        },
    )
    assert response.status_code == 201, response.text
    work = response.json()
    assert work["moderation_status"] == "reviewing"
    assert work["raw_verified"] is True
    assert work["partition"] in {"review", "gallery"}
    assert any(item["license_type"] == "raw_study" for item in work["licenses"])


def test_comment_quality_and_weighted_score(client):
    work_id = client.get("/v1/works?partition=gallery").json()[0]["id"]
    generic = client.post(
        f"/v1/works/{work_id}/reviews",
        json={"reviewer_id": "new-user", "author_name": "New", "body": "好看", "score": 100},
    )
    assert generic.status_code == 201
    assert generic.json()["folded"] is True

    detailed = client.post(
        f"/v1/works/{work_id}/reviews",
        json={
            "reviewer_id": "new-user",
            "author_name": "New",
            "body": "主体与右侧路牌重叠，建议降低机位并减少背景干扰；暗部曝光仍有 RAW 调整空间。",
            "score": 82,
        },
    )
    assert detailed.status_code == 201
    assert detailed.json()["metrics"]["quality"] > generic.json()["metrics"]["quality"]
    assert detailed.json()["folded"] is False


def test_blind_pair_hides_social_identity_and_records_vote(client):
    pair_response = client.get("/v1/blind/pair?sequence=0")
    assert pair_response.status_code == 200
    pair = pair_response.json()
    assert pair["left"]["author_name"] == "隐藏"
    assert pair["left"]["followers"] == 0
    vote = client.post(
        "/v1/blind/vote",
        json={
            "voter_id": "blind-tester",
            "left_work_id": pair["left"]["id"],
            "right_work_id": pair["right"]["id"],
            "winner_work_id": pair["left"]["id"],
        },
    )
    assert vote.status_code == 200
    assert "weight" in vote.json()["message"]


def test_appeal_report_and_license_snapshot(client):
    work = client.get("/v1/works?partition=gallery").json()[0]
    work_id = work["id"]
    report = client.post(f"/v1/works/{work_id}/reports", json={"reporter_id": "reporter", "reason": "test report reason"})
    assert report.status_code == 201

    purchase = client.post(f"/v1/works/{work_id}/licenses/preview", json={"buyer_id": "buyer"})
    assert purchase.status_code == 201
    assert purchase.json()["granted"] is True
    assert purchase.json()["buyer_id"] == "buyer"

    appeal = client.post(f"/v1/works/{work_id}/appeals", json={"author_id": "author", "reason": "please review again"})
    assert appeal.status_code == 201
    updated = client.get(f"/v1/works/{work_id}").json()
    assert updated["moderation_status"] == "appealing"
    assert updated["partition"] == "archive"


def test_favorite_is_idempotent_per_user(client):
    work = client.get("/v1/works").json()[0]
    work_id = work["id"]
    start = work["favorites"]

    first = client.post(f"/v1/works/{work_id}/favorite", json={"user_id": "favorite-user", "favorite": True})
    second = client.post(f"/v1/works/{work_id}/favorite", json={"user_id": "favorite-user", "favorite": True})
    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["favorites"] == start + 1
    assert second.json()["favorites"] == start + 1

    removed = client.post(f"/v1/works/{work_id}/favorite", json={"user_id": "favorite-user", "favorite": False})
    removed_again = client.post(f"/v1/works/{work_id}/favorite", json={"user_id": "favorite-user", "favorite": False})
    assert removed.json()["favorites"] == start
    assert removed_again.json()["favorites"] == start


def test_unsupported_extreme_score_is_visible_but_not_counted(client):
    work = client.get("/v1/works").json()[0]
    before = work["score"]
    response = client.post(
        f"/v1/works/{work['id']}/reviews",
        json={"reviewer_id": "extreme-user", "author_name": "Extreme", "body": "垃圾", "score": 1},
    )
    assert response.status_code == 201
    review = response.json()
    assert review["folded"] is True
    assert review["score_counted"] is False
    after = client.get(f"/v1/works/{work['id']}").json()["score"]
    assert after == before


def test_duplicate_blind_pair_vote_has_zero_weight(client):
    pair = client.get("/v1/blind/pair?sequence=0").json()
    payload = {
        "voter_id": "repeat-voter",
        "left_work_id": pair["left"]["id"],
        "right_work_id": pair["right"]["id"],
        "winner_work_id": pair["left"]["id"],
    }
    first = client.post("/v1/blind/vote", json=payload)
    score_after_first = client.get(f"/v1/works/{pair['left']['id']}").json()["score"]
    second = client.post("/v1/blind/vote", json=payload)
    score_after_second = client.get(f"/v1/works/{pair['left']['id']}").json()["score"]
    assert first.status_code == 200
    assert second.status_code == 200
    assert "weight 0.00" in second.json()["message"]
    assert score_after_second == score_after_first


def test_raw_download_requires_license_and_duplicate_purchase_is_blocked(client):
    upload = client.post(
        "/v1/works",
        data={
            "title": "Licensed RAW",
            "author_name": "Tester",
            "handle": "@tester",
            "allow_raw": "true",
        },
        files={
            "image": ("licensed.jpg", b"jpeg-content-for-license", "image/jpeg"),
            "raw": ("licensed.dng", b"raw-content-for-license", "application/octet-stream"),
        },
    )
    assert upload.status_code == 201
    work = upload.json()
    denied = client.get(f"/v1/works/{work['id']}/raw", params={"buyer_id": "licensed-buyer"})
    assert denied.status_code == 403

    purchase = client.post(
        f"/v1/works/{work['id']}/licenses/raw_study",
        json={"buyer_id": "licensed-buyer"},
    )
    assert purchase.status_code == 201
    raw = client.get(f"/v1/works/{work['id']}/raw", params={"buyer_id": "licensed-buyer"})
    assert raw.status_code == 200
    assert raw.content == b"raw-content-for-license"

    duplicate = client.post(
        f"/v1/works/{work['id']}/licenses/raw_study",
        json={"buyer_id": "licensed-buyer"},
    )
    assert duplicate.status_code == 409
