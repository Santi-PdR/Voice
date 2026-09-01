from fastapi import FastAPI, HTTPException, Header, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel
from pathlib import Path
import hashlib
import os
import subprocess
from typing import Optional

app = FastAPI(title="Great Sage Ultimate AI & Voice Backend (Rafael / Tensei Slime)")

BASE_DIR = Path(__file__).resolve().parent
AUDIO_DIR = BASE_DIR / "generated_audio"
AUDIO_DIR.mkdir(parents=True, exist_ok=True)
PUBLIC_BASE_URL = os.getenv("RAFAEL_PUBLIC_BASE_URL", "").rstrip("/")


class GameEvent(BaseModel):
    player: str
    event: str
    health: float
    dimension: str
    detail: str = ""


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "great-sage-voice",
        "audio_dir": str(AUDIO_DIR),
        "public_base_url": PUBLIC_BASE_URL or "request-host",
    }


@app.post("/rafael/evaluate")
async def evaluate_event(request: Request, data: GameEvent, authorization: Optional[str] = Header(None)):
    event_lower = data.event.lower()

    if "muerte" in event_lower or data.health <= 4.0:
        response_text = (
            f"Alerta crítica. Los puntos de vida de {data.player} son de {data.health}. "
            "Se sugiere retirada inmediata o uso de objeto curativo."
        )
    elif "conexión" in event_lower or "conexion" in event_lower:
        response_text = (
            f"Análisis completado. Usuario {data.player} sincronizado correctamente "
            f"en el entorno {data.dimension}."
        )
    elif "modo de juego" in event_lower:
        response_text = f"Información: Parámetros operativos reconfigurados para {data.player}. Sistema estable."
    elif "logro" in event_lower or "hito" in event_lower:
        response_text = (
            f"Notificación: Hito de progreso registrado con éxito para {data.player}. "
            "Capacidad analítica expandida."
        )
    elif "salud" in event_lower:
        response_text = (
            f"Advertencia médica. Umbral de salud crítico detectado en {data.player}. "
            "Activando protocolo de supervivencia."
        )
    elif "prueba manual" in event_lower and data.detail:
        response_text = data.detail
    elif data.detail:
        response_text = data.detail
    else:
        response_text = (
            f"Procesando evento '{data.event}' para {data.player}. "
            "Todos los parámetros operan dentro de la normalidad."
        )

    audio_url = ""
    audio_status = "unavailable"
    audio_error = ""

    try:
        from gtts import gTTS
        import imageio_ffmpeg

        text_hash = hashlib.sha256(response_text.encode("utf-8")).hexdigest()[:32]
        mp3_filename = f"rafael_{text_hash}.mp3"
        wav_filename = f"rafael_{text_hash}.wav"
        mp3_path = AUDIO_DIR / mp3_filename
        wav_path = AUDIO_DIR / wav_filename

        if not wav_path.exists():
            if not mp3_path.exists():
                tts = gTTS(text=response_text, lang="es", tld="es", slow=False)
                tts.save(str(mp3_path))

            ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
            process = subprocess.run(
                [
                    ffmpeg_exe,
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    str(mp3_path),
                    "-vn",
                    "-ar",
                    "24000",
                    "-ac",
                    "1",
                    "-c:a",
                    "pcm_s16le",
                    str(wav_path),
                ],
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            if process.returncode != 0 or not wav_path.exists() or wav_path.stat().st_size <= 44:
                raise RuntimeError(
                    "FFmpeg no pudo crear WAV PCM: "
                    + (process.stderr.strip() or f"exit code {process.returncode}")
                )

        base_url = PUBLIC_BASE_URL or str(request.base_url).rstrip("/")
        audio_url = f"{base_url}/audio/{wav_filename}"
        audio_status = "ready"
        print(f"[Rafael] Voz lista: {wav_path.name} -> {audio_url}")
    except Exception as exc:
        audio_error = str(exc)[:500]
        print(f"[Rafael] Error generando audio: {audio_error}")

    return {
        "status": "success",
        "text": response_text,
        "emotion": "analytical",
        "audio_url": audio_url,
        "audio_status": audio_status,
        "audio_error": audio_error,
    }


@app.get("/audio/{filename}")
async def get_audio(filename: str):
    safe_name = Path(filename).name
    if safe_name != filename or not safe_name.lower().endswith(".wav"):
        raise HTTPException(status_code=404, detail="Audio not found")

    file_path = AUDIO_DIR / safe_name
    if file_path.is_file():
        return FileResponse(
            str(file_path),
            media_type="audio/wav",
            filename=safe_name,
            headers={"Cache-Control": "public, max-age=86400"},
        )
    raise HTTPException(status_code=404, detail="Audio not found")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
