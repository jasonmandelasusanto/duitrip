import os
import json
import base64
import logging
import firebase_admin
from firebase_admin import credentials
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse, JSONResponse

from app.config import settings
from app.routers import users, trips, members, expenses, settlements, analytics, exchange_rates, categories

logger = logging.getLogger(__name__)

# Initialize Firebase Admin SDK
def _init_firebase():
    if firebase_admin._apps:
        return
    if settings.FIREBASE_SERVICE_ACCOUNT_JSON:
        sa_bytes = base64.b64decode(settings.FIREBASE_SERVICE_ACCOUNT_JSON)
        sa_dict = json.loads(sa_bytes.decode("utf-8"))
        cred = credentials.Certificate(sa_dict)
        firebase_admin.initialize_app(credential=cred, options={"projectId": settings.FIREBASE_PROJECT_ID})
    else:
        firebase_admin.initialize_app(options={"projectId": settings.FIREBASE_PROJECT_ID})


_init_firebase()

app = FastAPI(
    title="Duitrip API",
    version="1.0.0",
    docs_url="/api/docs",
    redoc_url="/api/redoc",
    openapi_url="/api/openapi.json",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(users.router, prefix="/api/users", tags=["users"])
app.include_router(trips.router, prefix="/api/trips", tags=["trips"])
app.include_router(members.router, prefix="/api/trips", tags=["members"])
app.include_router(expenses.router, prefix="/api/trips", tags=["expenses"])
app.include_router(settlements.router, prefix="/api/trips", tags=["settlements"])
app.include_router(analytics.router, prefix="/api/trips", tags=["analytics"])
app.include_router(exchange_rates.router, prefix="/api/exchange-rates", tags=["exchange-rates"])
app.include_router(categories.router, prefix="/api/trips", tags=["categories"])


@app.get("/health")
def health():
    return {"status": "ok"}


# ─── Serve React SPA static files (production single-container mode) ──────────
static_dir = os.path.join(os.path.dirname(__file__), "..", "static")
assets_dir = os.path.join(static_dir, "assets")

if os.path.isdir(static_dir):
    if os.path.isdir(assets_dir):
        app.mount("/assets", StaticFiles(directory=assets_dir), name="assets")

    @app.get("/{full_path:path}", include_in_schema=False)
    async def serve_spa(full_path: str):
        file_path = os.path.join(static_dir, full_path)
        if os.path.isfile(file_path):
            return FileResponse(file_path)
        return FileResponse(os.path.join(static_dir, "index.html"))
else:
    logger.warning("Static dir not found at %s — serving API only (dev mode)", static_dir)

    @app.get("/", include_in_schema=False)
    async def root():
        return JSONResponse({"message": "Duitrip API is running"})
