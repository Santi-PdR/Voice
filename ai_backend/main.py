from fastapi import FastAPI, HTTPException, Header, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel
import os
import hashlib
from typing import Optional

app = FastAPI(title="Great Sage Ultimate AI & Voice Backend (Rafael / Tensei Slime)")

AUDIO_DIR = "generated_audio"
os.makedirs(AUDIO_DIR, exist_ok=True)

class GameEvent(BaseModel):
    player: str
    event: str
    health: float
    dimension: str

@app.post("/rafael/evaluate")
async def evaluate_event(request: Request, data: GameEvent, authorization: Optional[str] = Header(None)):
    event_lower = data.event.lower()
    
    # Frases analíticas y frías características de Rafael (Gran Sabio) del anime
    if "muerte" in event_lower or data.health <= 4.0:
        response_text = f"Alerta crítica. Los puntos de vida de {data.player} son de {data.health}. Se sugiere retirada inmediata o uso de objeto curativo."
    elif "conexión" in event_lower:
        response_text = f"Análisis completado. Usuario {data.player} sincronizado correctamente en el entorno {data.dimension}."
    elif "modo de juego" in event_lower:
        response_text = f"Información: Parámetros operativos reconfigurados para {data.player}. Sistema estable."
    elif "logro" in event_lower:
        response_text = f"Notificación: Hito de progreso registrado con éxito para {data.player}. Capacidad analítica expandida."
    elif "salud" in event_lower:
        response_text = f"Advertencia médica. Umbral de salud crítico detectado en {data.player}. Activando protocolo de supervivencia."
    else:
        response_text = f"Procesando evento '{data.event}' para {data.player}. Todos los parámetros operan dentro de la normalidad."

    audio_url = ""
    
    # Generar voz real optimizada para emular la claridad y tono formal de Rafael
    try:
        from gtts import gTTS
        from pydub import AudioSegment
        
        # Usamos es-ES con velocidad ligeramente optimizada para mayor precisión analítica
        tts = gTTS(text=response_text, lang='es', tld='es', slow=False)
        
        text_hash = hashlib.md5(response_text.encode('utf-8')).hexdigest()
        mp3_filename = f"rafael_anime_{text_hash}.mp3"
        wav_filename = f"rafael_anime_{text_hash}.wav"
        
        mp3_path = os.path.join(AUDIO_DIR, mp3_filename)
        wav_path = os.path.join(AUDIO_DIR, wav_filename)
        
        if not os.path.exists(wav_path):
            if not os.path.exists(mp3_path):
                tts.save(mp3_path)
            # Procesar audio con pydub para darle un toque sutil y nítido de IA/anime (frecuencia limpia PCM WAV 24kHz)
            sound = AudioSegment.from_mp3(mp3_path)
            sound = sound.set_frame_rate(24000).set_channels(1)
            sound.export(wav_path, format="wav")
            
        base_url = str(request.base_url).rstrip('/')
        audio_url = f"{base_url}/audio/{wav_filename}"
    except Exception as e:
        print(f"Error generando audio de Rafael: {e}")

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
        return FileResponse(file_path, media_type="audio/wav")
    raise HTTPException(status_code=404, detail="Audio not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
