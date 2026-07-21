.PHONY: backend android test clean

backend:
	cd backend && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

test:
	cd backend && pytest -q

android:
	cd android && gradle :app:assembleDebug

clean:
	rm -rf backend/.pytest_cache backend/**/__pycache__ android/.gradle android/**/build
