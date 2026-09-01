from fastapi import FastAPI, HTTPException, Header
from fastapi.responses import FileResponse
from pydantic import BaseModel
import os
from typing import Optional

app = FastAPI(title="Great Sage AI Backend (Rafael / Tensei Slime) - Server Owner Edition")

# Directorio opcional para almacenar audios generados temporalmente
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
async def evaluate_event(data: GameEvent, authorization: Optional[str] = Header(None)):
    # El owner del servidor puede conectar aquí su LLM (OpenAI, Anthropic, Ollama, etc.)
    # y su motor de Text-to-Speech (ElevenLabs, Coqui TTS, gTTS, etc.).

    event_lower = data.event.lower()
    
    if "muerte" in event_lower or data.health <= 4.0:
        response_text = f"Alerta crítica. Los puntos de vida de {data.player} son de {data.health}. Se sugiere precaución inmediata."
    elif "conexión" in event_lower:
        response_text = f"Análisis completado. Usuario {data.player} sincronizado correctamente en {data.dimension}."
    elif "objeto" in event_lower:
        response_text = f"Información: Se ha registrado un objeto de interés en posesión de {data.player}."
    else:
        response_text = f"Procesando evento '{data.event}' para {data.player}. Todo opera dentro de parámetros normales."

    audio_url = ""
    
    # Opcional: Si el owner desea generar audio TTS automáticamente con gTTS (si está instalado)
    # try:
    #     from gtts import gTTS
    #     tts = gTTS(text=response_text, lang='es', slow=False)
    #     audio_path = os.path.join(AUDIO_DIR, f"{data.player}_{abs(hash(response_text))}.wav")
    #     # gtts genera mp3, se puede guardar o convertir a wav para Java
    #     mp3_path = audio_path.replace(".wav", ".mp3")
    #     tts.save(mp3_path)
    #     # audio_url = f"http://tu-servidor-ip:8000/audio/{os.path.basename(mp3_path)}"
    # except Exception as e:
    #     pass

    return {
        "status": "success",
        "text": response_text,
        "emotion": "analytical",
        "audio_url": audio_url # URL opcional para que el mod reproduzca la voz de Rafael
    }

@app.get("/audio/{filename}")
async def get_audio(filename: str):
    file_path = os.path.join(AUDIO_DIR, filename)
    if os.path.exists(file_path):
        return FileResponse(file_path)
    raise HTTPException(status_code=404, detail="Audio not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
