#!/usr/bin/env python3

import os
import json
import traceback
from typing import Optional, Dict, Any

from flask import Flask, request, jsonify
import tinytuya

# =========================
# CONFIG & AUTO-SETUP
# =========================

# No Android, usar o diretório de dados do app
try:
    from android.storage import app_storage_path
    BASE_DIR = app_storage_path()
except ImportError:
    # Fallback se não estiver no Android
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CONFIG_PATH = os.path.join(BASE_DIR, "config.json")

def create_config_if_needed():
    """Cria o config.json com nome do site/tablet."""
    if not os.path.exists(CONFIG_PATH):
        # Nome padrão - será atualizado pelo Kotlin via update_site_name()
        site = "ANDROID_DEVICE"
        
        cfg = {
            "site_name": site
        }
        
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=4, ensure_ascii=False)
        
        print(f"[OK] config.json criado com site_name = {site}")

def update_site_name(new_name: str):
    """Atualiza o nome do site no config.json"""
    cfg = {
        "site_name": new_name
    }
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=4, ensure_ascii=False)
    global SITE_NAME
    SITE_NAME = new_name
    print(f"[OK] site_name atualizado para = {new_name}")

# cria se não existir
create_config_if_needed()

# carrega o config
if os.path.exists(CONFIG_PATH):
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    SITE_NAME: str = cfg.get("site_name", "SITE_DESCONHECIDO")
else:
    SITE_NAME = "SITE_DESCONHECIDO"

print(f"[INFO] Servidor local iniciado para SITE = {SITE_NAME}")

def log(msg: str) -> None:
    print(msg, flush=True)

# =========================
# DISCOVERY / CACHE DE IP
# =========================

DEVICE_CACHE: Dict[str, str] = {}

def scan_and_print_devices() -> None:
    """Faz um scan na rede e imprime todos os dispositivos Tuya encontrados."""
    log("[SCAN] Iniciando scan de dispositivos Tuya na rede...")
    
    try:
        devices = tinytuya.deviceScan()
        
        if not isinstance(devices, dict):
            log(f"[SCAN] Resultado inesperado de deviceScan(): {type(devices)}")
            return
        
        if not devices:
            log("[SCAN] Nenhum dispositivo Tuya encontrado.")
            return
        
        log(f"[SCAN] {len(devices)} dispositivo(s) encontrado(s):")
        for ip, dev in devices.items():
            gwid = dev.get("gwId")
            ver = dev.get("version") or dev.get("ver")
            log(f"[SCAN] gwId={gwid}  ip={ip}  ver={ver}")
    
    except Exception as e:
        log(f"[SCAN] Erro ao escanear dispositivos Tuya: {e}")
        traceback.print_exc()

def scan_devices() -> Dict[str, Any]:
    """Faz um scan na rede e retorna todos os dispositivos Tuya encontrados em formato dict."""
    log("[SCAN] Iniciando scan de dispositivos Tuya na rede...")
    discovered_devices = {}
    
    try:
        devices = tinytuya.deviceScan()
        
        if not isinstance(devices, dict):
            log(f"[SCAN] Resultado inesperado de deviceScan(): {type(devices)}")
            return {}
        
        if not devices:
            log("[SCAN] Nenhum dispositivo Tuya encontrado.")
            return {}
        
        log(f"[SCAN] {len(devices)} dispositivo(s) encontrado(s):")
        for ip, dev in devices.items():
            gwid = dev.get("gwId")
            ver = dev.get("version") or dev.get("ver")
            log(f"[SCAN] gwId={gwid}  ip={ip}  ver={ver}")
            
            if gwid:
                discovered_devices[gwid] = {
                    "id": gwid,
                    "ip": ip,
                    "version": ver
                }
    
    except Exception as e:
        log(f"[SCAN] Erro ao escanear dispositivos Tuya: {e}")
        traceback.print_exc()
    
    return discovered_devices

def discover_tuya_ip(tuya_device_id: str) -> Optional[str]:
    """
    Tenta descobrir o IP LAN de um dispositivo Tuya pelo gwId (device_id),
    usando tinytuya.deviceScan() e guarda em cache.
    """
    # se já descobrimos antes, usa o cache
    if tuya_device_id in DEVICE_CACHE:
        ip_cached = DEVICE_CACHE[tuya_device_id]
        log(f"[DISCOVER] Usando IP em cache para {tuya_device_id}: {ip_cached}")
        return ip_cached
    
    log(f"[DISCOVER] Varrendo a rede para encontrar o device_id = {tuya_device_id} ...")
    
    try:
        devices = tinytuya.deviceScan()
        
        if not isinstance(devices, dict):
            log(f"[DISCOVER] Resultado inesperado de deviceScan(): {type(devices)}")
            return None
        
        log(f"[DISCOVER] deviceScan encontrou {len(devices)} dispositivo(s).")
        
        for ip, dev in devices.items():
            gwid = dev.get("gwId")
            dev_ip = dev.get("ip", ip)
            log(f"[DISCOVER] Achado gwId={gwid} ip={dev_ip}")
            if gwid == tuya_device_id:
                log(f"[DISCOVER] Encontrado! device_id={gwid} ip={dev_ip}")
                DEVICE_CACHE[tuya_device_id] = dev_ip
                return dev_ip
        
        log(f"[DISCOVER] Nenhum dispositivo encontrado com device_id = {tuya_device_id}")
        return None
    
    except Exception as e:
        log(f"[DISCOVER] Erro ao escanear dispositivos Tuya: {e}")
        traceback.print_exc()
        return None

# =========================
# TUYA
# =========================

def send_tuya_command(
    action: str,
    tuya_device_id: str,
    local_key: str,
    lan_ip: Optional[str],
    version: Optional[float] = None
) -> None:
    
    if not tuya_device_id:
        raise RuntimeError("Campo tuya_device_id é obrigatório")
    if not local_key:
        raise RuntimeError("Campo local_key é obrigatório")
    
    # Se não veio IP ou veio "auto", tenta descobrir
    if not lan_ip or str(lan_ip).lower() == "auto":
        log(f"[INFO] Nenhum lan_ip informado (ou 'auto'). Tentando descobrir IP do device {tuya_device_id}...")
        lan_ip = discover_tuya_ip(tuya_device_id)
        if not lan_ip:
            raise RuntimeError("Não foi possível descobrir o IP LAN do dispositivo Tuya.")
    
    # Garante que venha só IP, nada de 'http://'
    lan_ip = str(lan_ip).strip()
    if lan_ip.startswith("http://") or lan_ip.startswith("https://"):
        raise RuntimeError("lan_ip deve ser apenas o IP (ex: 192.168.0.50), sem http:// e sem porta.")
    
    # Se não veio version ou veio vazio, usa 3.3 como padrão
    if version is None or version == "":
        version = 3.3
    
    log(f"[INFO] [{SITE_NAME}] Enviando '{action}' → {tuya_device_id} @ {lan_ip} (versão {version})")
    
    d = tinytuya.OutletDevice(tuya_device_id, lan_ip, local_key)
    
    # Usa a versão recebida do JSON ou 3.3 como padrão
    d.set_version(version)
    
    if action == "on":
        resp = d.turn_on()
    elif action == "off":
        resp = d.turn_off()
    else:
        raise ValueError(f"Ação inválida: {action}")
    
    log(f"[DEBUG] Resposta do dispositivo: {resp}")

# =========================
# API HTTP
# =========================

app = Flask(__name__)

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "site": SITE_NAME}), 200

@app.route("/tuya/command", methods=["POST"])
def api_tuya_command():
    try:
        data: Dict[str, Any] = request.get_json(force=True, silent=False) or {}
        
        action = data.get("action")
        tuya_device_id = data.get("tuya_device_id")
        local_key = data.get("local_key")
        lan_ip = data.get("lan_ip")  # pode vir None, vazio ou "auto"
        version = data.get("version")  # pode vir None, vazio ou um número (ex: 3.3, 3.4)
        
        if action not in ("on", "off"):
            return jsonify({"ok": False, "error": "action deve ser 'on' ou 'off'"}), 400
        
        # Converte version para float se vier como string
        if version is not None and version != "":
            try:
                version = float(version)
            except (ValueError, TypeError):
                version = None
        
        send_tuya_command(
            action=action,
            tuya_device_id=tuya_device_id,
            local_key=local_key,
            lan_ip=lan_ip,
            version=version
        )
        
        return jsonify({"ok": True}), 200
    
    except Exception as e:
        err = str(e)
        log(f"[ERRO] API /tuya/command: {err}")
        traceback.print_exc()
        return jsonify({"ok": False, "error": err}), 500

@app.route("/tuya/devices", methods=["GET"])
def api_tuya_devices():
    """Retorna lista de dispositivos escaneados na rede"""
    try:
        devices = scan_devices()
        device_list = []
        for gwid, dev_info in devices.items():
            device_list.append({
                "id": gwid,
                "ip": dev_info.get("ip", ""),
                "version": dev_info.get("version", "")
            })
        return jsonify({"ok": True, "devices": device_list}), 200
    except Exception as e:
        err = str(e)
        log(f"[ERRO] API /tuya/devices: {err}")
        traceback.print_exc()
        return jsonify({"ok": False, "error": err}), 500

def start_server(host="0.0.0.0", port=8000):
    """Inicia o servidor Flask"""
    log(f"[START] Servidor Tuya local rodando em http://{host}:{port} (SITE={SITE_NAME})")
    # Faz o scan inicial
    scan_and_print_devices()
    app.run(host=host, port=port, debug=False, use_reloader=False)

