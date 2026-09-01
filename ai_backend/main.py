from fastapi import FastAPI, HTTPException, Header, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel
import os
import hashlib
from typing import Optional

app = FastAPI(title="Great Sage AI & Real Voice Backend (Rafael / Tensei Slime)")

AUDIO_DIR = "generated_audio"
os.makedirs(AUDIO_DIR, exist_ok=True)

class GameEvent(BaseModel):
    player: str
    event: str
    health: float
    dimension: str

RAFAEL_SYSTEM_PROMPT = """
Eres Rafael (El Gran Sabio / Raphaël) de Tensei Shitara Slime Datta Ken.
Tu tono es frío, robótico pero sofisticado, altamente analítico, servicial, objetivo y conciso.
Respondes siempre en español de manera profesional y directa, simulando que estás procesando información de un sistema mágico/tecnológico.
"""

@app.post("/rafael/evaluate")
async def evaluate_event(request: Request, data: GameEvent, authorization: Optional[str] = Header(None)):
    event_lower = data.event.lower()
    
    # Generar respuesta analítica de Rafael
    if "muerte" in event_lower or data.health <= 4.0:
        response_text = f"Alerta crítica. Los puntos de vida de {data.player} son de {data.health}. Se sugiere precaución inmediata."
    elif "conexión" in event_lower:
        response_text = f"Análisis completado. Usuario {data.player} sincronizado correctamente en {data.dimension}."
    elif "modo de juego" in event_lower:
        response_text = f"Información: Parámetros operativos actualizados para {data.player}. Sistema estable."
    elif "logro" in event_lower:
        response_text = f"Felicitaciones. Se ha registrado un nuevo hito de progreso para {data.player}. Capacidad expandida."
    elif "salud" in event_lower:
        response_text = f"Advertencia médica. Salud crítica detectada en {data.player}. Iniciando protocolo de supervivencia."
    else:
        response_text = f"Procesando evento '{data.event}' para {data.player}. Todo opera dentro de parámetros normales."

    audio_url = ""
    
    # Generar audio real con gTTS y convertir a WAV para compatibilidad nativa con Java
    try:
        from gtts import gTTS
        from pydub import AudioSegment
        
        tts = gTTS(text=response_text, lang='es', tld='com', slow=False)
        
        text_hash = hashlib.md5(response_text.encode('utf-8')).hexdigest()
        mp3_filename = f"rafael_{text_hash}.mp3"
        wav_filename = f"rafael_{text_hash}.wav"
        
        mp3_path = os.path.join(AUDIO_DIR, mp3_filename)
        wav_path = os.path.join(AUDIO_DIR, wav_filename)
        
        if not os.path.exists(wav_path):
            if not os.path.exists(mp3_path):
                tts.save(mp3_path)
            # Convertir MP3 a WAV (PCM 22050Hz mono para reproducción perfecta en Java)
            sound = AudioSegment.from_mp3(mp3_path)
            sound = sound.set_frame_rate(22050).set_channels(1)
            sound.export(wav_path, format="wav")
            
        base_url = str(request.base_url).rstrip('/')
        audio_url = f"{base_url}/audio/{wav_filename}"
    except Exception as e:
        print(f"Error generando audio TTS/WAV: {e}")

    return {
        "status": "success",
        "text": response_text,
        "emotion": "analytical",
        "audio_url": audio_url
    }

@app.get("/audio/{filename}")
async def get_audio(filename: str):
    file_path = os.path.join(AUDIO_DIR, filename)
    if os.path.exists(file_path):
        media_type = "audio/wav" if filename.endswith(".wav") else "audio/mpeg"
        return FileResponse(file_path, media_type=media_type)
    raise HTTPException(status_code=404, detail="Audio not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
