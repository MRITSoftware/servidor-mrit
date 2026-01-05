#!/usr/bin/env python3

import os
import json
import traceback
import threading
from typing import Optional, Dict, Any, List

from flask import Flask, request, jsonify
import tinytuya

# Tentar importar supabase, mas não falhar se não estiver disponível
try:
    from supabase import create_client
    try:
        from supabase import Client
    except ImportError:
        # Algumas versões não exportam Client diretamente
        Client = Any
    SUPABASE_AVAILABLE = True
except ImportError:
    SUPABASE_AVAILABLE = False
    Client = None
    # log será definido depois, então apenas print aqui
    print("[WARN] supabase não disponível - funcionalidades de banco desabilitadas")

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
            "site_name": site,
            "supabase": {
                "url": "",
                "anon_key": ""
            }
        }
        
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=4, ensure_ascii=False)
        
        print(f"[OK] config.json criado com site_name = {site}")

def update_site_name(new_name: str):
    """Atualiza o nome do site no config.json"""
    # Carregar config existente
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    else:
        cfg = {}
    
    cfg["site_name"] = new_name
    
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=4, ensure_ascii=False)
    
    global SITE_NAME
    SITE_NAME = new_name
    print(f"[OK] site_name atualizado para = {new_name}")

def update_supabase_config(url: str, anon_key: str):
    """Atualiza a configuração do Supabase no config.json"""
    # Carregar config existente
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    else:
        cfg = {}
    
    cfg["supabase"] = {
        "url": url,
        "anon_key": anon_key
    }
    
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=4, ensure_ascii=False)
    
    # Atualizar variável global
    global SUPABASE_CONFIG
    SUPABASE_CONFIG = cfg["supabase"]
    log(f"[OK] Configuração do Supabase atualizada")

# cria se não existir
create_config_if_needed()

# carrega o config
if os.path.exists(CONFIG_PATH):
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    SITE_NAME: str = cfg.get("site_name", "SITE_DESCONHECIDO")
    SUPABASE_CONFIG = cfg.get("supabase", {})
else:
    SITE_NAME = "SITE_DESCONHECIDO"
    SUPABASE_CONFIG = {}

# Garantir que SUPABASE_CONFIG tem a estrutura correta
if not isinstance(SUPABASE_CONFIG, dict):
    SUPABASE_CONFIG = {}

# Configurar Supabase automaticamente se as credenciais padrão estiverem disponíveis
# (pode ser configurado via variáveis de ambiente ou hardcoded para desenvolvimento)
DEFAULT_SUPABASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co"
DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"

# Se não há configuração, usar as credenciais padrão
if not SUPABASE_CONFIG.get("url") or not SUPABASE_CONFIG.get("anon_key"):
    try:
        update_supabase_config(DEFAULT_SUPABASE_URL, DEFAULT_SUPABASE_ANON_KEY)
        log("[INFO] Supabase configurado automaticamente com credenciais padrão")
    except Exception as e:
        log(f"[WARN] Não foi possível configurar Supabase automaticamente: {e}")

print(f"[INFO] Servidor local iniciado para SITE = {SITE_NAME}")

def log(msg: str) -> None:
    print(msg, flush=True)

# =========================
# DATABASE (SUPABASE)
# =========================

def get_supabase_client():
    """Cria cliente Supabase."""
    if not SUPABASE_AVAILABLE:
        raise RuntimeError("supabase não está disponível")
    
    url = SUPABASE_CONFIG.get("url")
    anon_key = SUPABASE_CONFIG.get("anon_key")
    
    if not url or not anon_key:
        raise RuntimeError("Configuração do Supabase não encontrada (url ou anon_key faltando)")
    
    try:
        supabase = create_client(url, anon_key)
        return supabase
    except Exception as e:
        log(f"[DB] Erro ao criar cliente Supabase: {e}")
        raise

def get_devices_from_db(tuya_device_ids: List[str]) -> Dict[str, Dict]:
    """
    Busca devices da tabela tuya_devices pelos tuya_device_id.
    Retorna um dict onde a chave é tuya_device_id e o valor é um dict com os dados.
    """
    if not SUPABASE_AVAILABLE or not SUPABASE_CONFIG.get("url"):
        return {}
    
    if not tuya_device_ids:
        return {}
    
    try:
        supabase = get_supabase_client()
        
        # Buscar devices usando Supabase
        # Usar .in_() para buscar múltiplos tuya_device_id
        response = supabase.table("tuya_devices").select("*").in_("tuya_device_id", tuya_device_ids).execute()
        
        result = {}
        for row in response.data:
            tuya_id = row.get('tuya_device_id')
            if tuya_id:
                result[tuya_id] = {
                    'id': str(row.get('id', '')),
                    'site_id': row.get('site_id'),
                    'tuya_device_id': tuya_id,
                    'name': row.get('name'),
                    'local_key': row.get('local_key'),
                    'lan_ip': row.get('lan_ip'),
                    'protocol_version': row.get('protocol_version')
                }
        
        log(f"[DB] Encontrados {len(result)} devices no banco")
        return result
        
    except Exception as e:
        log(f"[DB] Erro ao buscar devices: {e}")
        traceback.print_exc()
        return {}

def update_device_in_db(
    tuya_device_id: str,
    name: Optional[str] = None,
    local_key: Optional[str] = None,
    lan_ip: Optional[str] = None,
    protocol_version: Optional[str] = None
) -> bool:
    """
    Atualiza um device na tabela tuya_devices.
    Apenas atualiza os campos que foram fornecidos (não None).
    """
    if not SUPABASE_AVAILABLE or not SUPABASE_CONFIG.get("url"):
        return False
    
    try:
        supabase = get_supabase_client()
        
        # Construir dict com apenas os campos que foram fornecidos
        update_data = {}
        
        if name is not None:
            update_data['name'] = name
        
        if local_key is not None:
            update_data['local_key'] = local_key
        
        if lan_ip is not None:
            update_data['lan_ip'] = lan_ip
        
        if protocol_version is not None:
            update_data['protocol_version'] = protocol_version
        
        # updated_at será atualizado automaticamente pelo banco (default now())
        # Mas podemos forçar também se necessário
        
        if not update_data:
            return False
        
        # Atualizar usando Supabase
        response = supabase.table("tuya_devices").update(update_data).eq("tuya_device_id", tuya_device_id).execute()
        
        if response.data and len(response.data) > 0:
            log(f"[DB] Device {tuya_device_id} atualizado com sucesso")
            return True
        else:
            log(f"[DB] Nenhum device encontrado com tuya_device_id = {tuya_device_id}")
            return False
        
    except Exception as e:
        log(f"[DB] Erro ao atualizar device: {e}")
        traceback.print_exc()
        return False

# =========================
# DISCOVERY / CACHE DE IP
# =========================

DEVICE_CACHE: Dict[str, str] = {}

def scan_and_print_devices() -> None:
    """Faz um scan na rede e imprime todos os dispositivos Tuya encontrados."""
    log("[SCAN] Iniciando scan de dispositivos Tuya na rede...")
    
    try:
        # Usar timeout para evitar travamentos
        devices = scan_with_timeout(30)  # 30 segundos de timeout
        
        if devices is None:
            log("[SCAN] Timeout ou erro ao escanear dispositivos")
            return
        
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
        # Usar timeout para evitar travamentos
        devices = scan_with_timeout(30)  # 30 segundos de timeout
        
        if devices is None:
            log("[SCAN] Timeout ou erro ao escanear dispositivos")
            return {}
        
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

def scan_with_timeout(timeout_seconds: int = 30) -> Optional[Dict]:
    """Executa deviceScan com timeout para evitar travamentos."""
    result = [None]
    exception = [None]
    
    def scan_thread():
        try:
            result[0] = tinytuya.deviceScan()
        except Exception as e:
            exception[0] = e
    
    thread = threading.Thread(target=scan_thread, daemon=True)
    thread.start()
    thread.join(timeout=timeout_seconds)
    
    if thread.is_alive():
        log(f"[SCAN] Timeout após {timeout_seconds} segundos")
        return None
    
    if exception[0]:
        log(f"[SCAN] Exceção durante scan: {exception[0]}")
        return None
    
    return result[0]

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
        # Usar timeout para evitar travamentos
        devices = scan_with_timeout(30)  # 30 segundos de timeout
        
        if devices is None:
            log(f"[DISCOVER] Timeout ou erro ao escanear dispositivos")
            return None
        
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
    
    try:
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
    except Exception as e:
        # Limpar cache se houver erro de conexão
        if tuya_device_id in DEVICE_CACHE:
            log(f"[INFO] Limpando cache de IP para {tuya_device_id} devido a erro")
            del DEVICE_CACHE[tuya_device_id]
        raise RuntimeError(f"Erro ao enviar comando para dispositivo: {e}")

# =========================
# API HTTP
# =========================

app = Flask(__name__)

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "site": SITE_NAME}), 200

@app.route("/config/supabase", methods=["POST"])
def api_config_supabase():
    """
    Configura as credenciais do Supabase.
    
    Body:
    {
        "url": "https://kihyhoqbrkwbfudttevo.supabase.co",
        "anon_key": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
    """
    try:
        data = request.get_json(force=True, silent=False) or {}
        url = data.get("url")
        anon_key = data.get("anon_key")
        
        if not url or not anon_key:
            return jsonify({
                "ok": False,
                "error": "url e anon_key são obrigatórios"
            }), 400
        
        update_supabase_config(url, anon_key)
        
        return jsonify({
            "ok": True,
            "message": "Configuração do Supabase atualizada com sucesso"
        }), 200
        
    except Exception as e:
        err = str(e)
        log(f"[ERRO] API /config/supabase: {err}")
        traceback.print_exc()
        return jsonify({"ok": False, "error": err}), 500

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

@app.route("/tuya/sync", methods=["POST"])
def api_sync_devices():
    """
    Sincroniza devices encontrados na rede LAN com a tabela tuya_devices.
    Para cada device encontrado na rede, se existir na tabela com mesmo tuya_device_id,
    atualiza: lan_ip, protocol_version (sempre que disponíveis do scan).
    Opcionalmente pode receber name e local_key no body para atualizar também.
    
    Body opcional:
    {
        "devices": {
            "tuya_device_id_1": {
                "name": "Nome do Device",
                "local_key": "local_key_da_placa"
            },
            ...
        }
    }
    """
    try:
        log("[SYNC] Iniciando sincronização de devices...")
        
        # Ler dados opcionais do body
        body_data = request.get_json(silent=True) or {}
        devices_data = body_data.get("devices", {})
        
        # 1) Fazer scan LAN para pegar devices na rede
        lan_devices = scan_devices()
        
        if not lan_devices:
            log("[SYNC] Nenhum device encontrado na rede")
            return jsonify({
                "ok": True,
                "message": "Nenhum device encontrado na rede",
                "updated": 0
            }), 200
        
        log(f"[SYNC] Encontrados {len(lan_devices)} devices na rede")
        
        # 2) Buscar devices no banco que correspondem aos encontrados na rede
        tuya_ids = list(lan_devices.keys())
        db_devices = get_devices_from_db(tuya_ids)
        
        if not db_devices:
            log("[SYNC] Nenhum device da rede encontrado no banco de dados")
            return jsonify({
                "ok": True,
                "message": "Nenhum device da rede encontrado no banco",
                "updated": 0
            }), 200
        
        # 3) Para cada device encontrado na rede E no banco, atualizar
        updated_count = 0
        updated_devices = []
        
        for tuya_id, lan_info in lan_devices.items():
            if tuya_id not in db_devices:
                continue
            
            db_info = db_devices[tuya_id]
            lan_ip = lan_info.get("ip")
            protocol_version = lan_info.get("version")
            
            # Converter version para string se necessário
            if protocol_version:
                protocol_version = str(protocol_version)
            
            # Buscar dados opcionais do body (name e local_key)
            device_extra_data = devices_data.get(tuya_id, {})
            name_from_body = device_extra_data.get("name")
            local_key_from_body = device_extra_data.get("local_key")
            
            # Preparar dados para atualização
            update_needed = False
            update_data = {}
            
            # Sempre atualizar lan_ip e protocol_version se disponíveis do scan
            if lan_ip and lan_ip != db_info.get('lan_ip'):
                update_data['lan_ip'] = lan_ip
                update_needed = True
            
            if protocol_version and protocol_version != db_info.get('protocol_version'):
                update_data['protocol_version'] = protocol_version
                update_needed = True
            
            # Atualizar name se fornecido no body
            if name_from_body and name_from_body != db_info.get('name'):
                update_data['name'] = name_from_body
                update_needed = True
            
            # Atualizar local_key se fornecido no body
            if local_key_from_body and local_key_from_body != db_info.get('local_key'):
                update_data['local_key'] = local_key_from_body
                update_needed = True
            
            if update_needed:
                success = update_device_in_db(
                    tuya_device_id=tuya_id,
                    name=update_data.get('name'),
                    local_key=update_data.get('local_key'),
                    lan_ip=update_data.get('lan_ip'),
                    protocol_version=update_data.get('protocol_version')
                )
                
                if success:
                    updated_count += 1
                    updated_devices.append({
                        "tuya_device_id": tuya_id,
                        "updated_fields": list(update_data.keys())
                    })
                    log(f"[SYNC] Device {tuya_id} atualizado: {list(update_data.keys())}")
        
        log(f"[SYNC] Sincronização concluída: {updated_count} devices atualizados")
        
        return jsonify({
            "ok": True,
            "message": f"{updated_count} device(s) atualizado(s)",
            "updated": updated_count,
            "devices": updated_devices
        }), 200
        
    except Exception as e:
        err = str(e)
        log(f"[ERRO] API /tuya/sync: {err}")
        traceback.print_exc()
        return jsonify({"ok": False, "error": err}), 500

def start_server(host="0.0.0.0", port=8000):
    """Inicia o servidor Flask"""
    log(f"[START] Servidor Tuya local rodando em http://{host}:{port} (SITE={SITE_NAME})")
    # Faz o scan inicial
    scan_and_print_devices()
    app.run(host=host, port=port, debug=False, use_reloader=False)

