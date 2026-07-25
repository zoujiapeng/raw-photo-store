SHELL := /usr/bin/env bash
PYTHON ?= python3
VENV := .venv
PIP := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest
RUFF := $(VENV)/bin/ruff

.PHONY: setup backend test verify android android-test docker reset-dev clean

setup:
	$(PYTHON) -m venv $(VENV)
	$(PIP) install -e './backend[dev]'

backend: setup
	APP_ENV=development $(VENV)/bin/uvicorn app.main:app --app-dir backend --reload --host 0.0.0.0 --port 8000

test: setup
	cd backend && ../$(PYTEST) --cov=app --cov-report=term-missing --cov-fail-under=70

verify: setup
	./scripts/verify.sh

android:
	./android/gradlew :app:assembleDebug

android-test:
	./android/gradlew :app:testDebugUnitTest

docker:
	docker compose up --build

reset-dev:
	./scripts/reset-dev-data.sh

clean:
	rm -rf .venv backend/.pytest_cache backend/.ruff_cache android/.gradle
	find backend android -type d \
		\( -name '__pycache__' -o -name '*.egg-info' -o -name build \) -prune -exec rm -rf {} +
