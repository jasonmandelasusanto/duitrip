from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    FIREBASE_PROJECT_ID: str = "demo-duitrip"
    FIREBASE_SERVICE_ACCOUNT_JSON: str = ""
    ALLOWED_ORIGINS: str = "http://localhost:5173"
    SENDGRID_API_KEY: str = ""
    FROM_EMAIL: str = "noreply@duitrip.com"
    FIRESTORE_EMULATOR_HOST: str = ""
    FIREBASE_AUTH_EMULATOR_HOST: str = ""
    CLOUDFLARE_TURNSTILE_SECRET_KEY: str = ""

    @property
    def allowed_origins_list(self) -> List[str]:
        return [o.strip() for o in self.ALLOWED_ORIGINS.split(",")]

    @property
    def is_emulator(self) -> bool:
        return bool(self.FIRESTORE_EMULATOR_HOST or self.FIREBASE_AUTH_EMULATOR_HOST)

    class Config:
        env_file = ".env.local"
        env_file_encoding = "utf-8"


settings = Settings()
