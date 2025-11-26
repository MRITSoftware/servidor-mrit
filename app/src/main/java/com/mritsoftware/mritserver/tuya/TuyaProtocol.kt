package com.mritsoftware.mritserver.tuya

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object TuyaProtocol {
    
    private const val TAG = "TuyaProtocol"
    private const val TUYA_PORT = 6668
    private const val VERSION = 0x03
    
    /**
     * Envia comando para dispositivo Tuya na rede local
     */
    fun sendCommand(
        deviceId: String,
        localKey: String,
        ip: String,
        command: String, // "on" ou "off"
        version: Double = 3.3
    ): Boolean {
        return try {
            Log.d(TAG, "Enviando comando '$command' para $deviceId @ $ip")
            
            val dps = when (command) {
                "on" -> mapOf("1" to true)
                "off" -> mapOf("1" to false)
                else -> return false
            }
            
            val payload = buildCommandPayload(dps, version)
            val encrypted = encrypt(payload, localKey, version)
            val packet = buildPacket(encrypted, version)
            
            sendUdpPacket(ip, packet)
            
            Log.d(TAG, "Comando enviado com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar comando Tuya", e)
            false
        }
    }
    
    /**
     * Constrói o payload do comando
     */
    private fun buildCommandPayload(dps: Map<String, Any>, version: Double): ByteArray {
        val json = org.json.JSONObject().apply {
            put("dps", org.json.JSONObject().apply {
                dps.forEach { (key, value) ->
                    put(key, value)
                }
            })
        }
        
        return json.toString().toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Criptografa o payload usando AES
     */
    private fun encrypt(data: ByteArray, key: String, version: Double): ByteArray {
        return try {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            
            val cipher = if (version >= 3.4) {
                // AES-256-CBC para versão 3.4+
                Cipher.getInstance("AES/CBC/PKCS5Padding")
            } else {
                // AES-128-ECB para versão 3.3
                Cipher.getInstance("AES/ECB/PKCS5Padding")
            }
            
            if (version >= 3.4) {
                // Usar IV para CBC
                val iv = ByteArray(16) // IV zero para Tuya
                val ivSpec = IvParameterSpec(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            }
            
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criptografar", e)
            data
        }
    }
    
    /**
     * Constrói o pacote UDP completo
     */
    private fun buildPacket(encryptedData: ByteArray, version: Double): ByteArray {
        val versionByte = if (version >= 3.4) 0x04 else 0x03
        val command = 0x0D // Comando de controle
        val sequence = 0x0000
        
        val buffer = ByteBuffer.allocate(4 + 4 + 4 + encryptedData.size + 4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.putInt(0x000055AA.toInt()) // Magic number
        buffer.putInt(versionByte) // Version
        buffer.putInt(command) // Command
        buffer.putInt(sequence) // Sequence
        
        // Payload
        buffer.put(encryptedData)
        
        // CRC (simplificado - em produção calcular CRC real)
        val crc = calculateCRC(buffer.array(), 0, buffer.position())
        buffer.putInt(crc)
        
        return buffer.array()
    }
    
    /**
     * Calcula CRC simplificado (em produção usar algoritmo Tuya real)
     */
    private fun calculateCRC(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until (offset + length)) {
            crc = (crc + (data[i].toInt() and 0xFF)) and 0xFFFFFFFF.toInt()
        }
        return crc
    }
    
    /**
     * Envia pacote UDP para o dispositivo
     */
    private fun sendUdpPacket(ip: String, packet: ByteArray) {
        val socket = DatagramSocket()
        try {
            val address = InetAddress.getByName(ip)
            val datagramPacket = DatagramPacket(
                packet,
                packet.size,
                address,
                TUYA_PORT
            )
            socket.send(datagramPacket)
            Log.d(TAG, "Pacote UDP enviado para $ip:$TUYA_PORT")
        } finally {
            socket.close()
        }
    }
    
    /**
     * Descobre dispositivos Tuya na rede local
     */
    fun discoverDevices(timeout: Int = 5000): Map<String, String> {
        val devices = mutableMapOf<String, String>()
        
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeout
            
            // Pacote de descoberta Tuya
            val discoveryPacket = buildDiscoveryPacket()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(
                discoveryPacket,
                discoveryPacket.size,
                broadcastAddress,
                TUYA_PORT
            )
            
            socket.send(packet)
            Log.d(TAG, "Pacote de descoberta enviado")
            
            // Tentar receber respostas
            val buffer = ByteArray(1024)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    socket.receive(receivePacket)
                    val deviceId = extractDeviceId(buffer)
                    val ip = receivePacket.address.hostAddress
                    if (deviceId != null && ip != null) {
                        devices[deviceId] = ip
                        Log.d(TAG, "Dispositivo encontrado: $deviceId @ $ip")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }
            }
            
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao descobrir dispositivos", e)
        }
        
        return devices
    }
    
    /**
     * Constrói pacote de descoberta
     */
    private fun buildDiscoveryPacket(): ByteArray {
        val buffer = ByteBuffer.allocate(20)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.putInt(0x000055AA.toInt()) // Magic
        buffer.putInt(0x00000000) // Version/Command
        buffer.putInt(0x00000000) // Sequence
        buffer.putInt(0x00000000) // Length
        buffer.putInt(0x0000AA55.toInt()) // CRC
        
        return buffer.array()
    }
    
    /**
     * Extrai device ID da resposta
     */
    private fun extractDeviceId(data: ByteArray): String? {
        // Implementação simplificada - em produção parsear resposta completa
        return try {
            // Procurar por padrão de device ID na resposta
            val stringData = String(data, Charsets.UTF_8)
            // Regex ou parsing específico do protocolo Tuya
            null // Por enquanto retorna null - precisa implementar parsing real
        } catch (e: Exception) {
            null
        }
    }
}

