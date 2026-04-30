import hmac
import hashlib
import os

class Tokenizer:
    def __init__(self, key: bytes | None = None):
        self.key = key or os.environ.get("TOKEN_HMAC_KEY", "demo-key-change-me").encode()

    def token(self, raw_value: str, namespace: str) -> str:
        normalized = " ".join(str(raw_value).strip().lower().split())
        msg = f"{namespace}:{normalized}".encode()
        return hmac.new(self.key, msg, hashlib.sha256).hexdigest()

if __name__ == "__main__":
    tokenizer = Tokenizer()
    print(tokenizer.token("John Smith", "payee_name"))
