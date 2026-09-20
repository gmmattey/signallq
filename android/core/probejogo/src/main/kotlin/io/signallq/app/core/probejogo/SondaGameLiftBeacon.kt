package io.signallq.app.core.probejogo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom

private const val MAGIC = "SQPB" // SignallQ Probe Beacon — payload próprio, formato do LagCheck (docs/09)
private const val VERSION: Byte = 0x01
private const val SESSION_ID_BYTES = 16
private const val SEQUENCE_BYTES = 4
private const val NONCE_BYTES = 8
private const val PAYLOAD_BYTES = 4 + 1 + SESSION_ID_BYTES + SEQUENCE_BYTES + NONCE_BYTES // 33
private const val BUFFER_RESPOSTA_BYTES = 128

/**
 * Client do beacon UDP público de referência da AWS GameLift (ping beacon) —
 * https://docs.aws.amazon.com/gameliftservers/latest/developerguide/reference-udp-ping-beacons.html.
 * O beacon ecoa de volta qualquer corpo não vazio (<1024 bytes) enviado a ele; o payload
 * (magic + versão + sessão + sequência + nonce) segue o mesmo formato documentado pelo LagCheck
 * (produto irmão iOS, `docs/09-decisao-rede-piloto.md`), trocando apenas o magic (`SQPB`, marca
 * própria) pelo do LagCheck (`NLPG`) — garante que só um eco íntegro do payload exato enviado
 * conta como amostra válida, nunca uma resposta tardia ou de outro destino.
 *
 * Uma amostra por vez (bloqueante dentro do timeout) — sem múltiplos probes pendentes
 * simultâneos, então a correlação por conteúdo exato é suficiente; não precisa do ledger de
 * sessão/sequência que o LagCheck usa para pipeline concorrente.
 *
 * Retorna amostras cruas (RTT em ms, `null` quando não houve resposta válida dentro do
 * timeout) — nunca trata timeout como sucesso, nunca fabrica RTT (AGENTS.md §8). A análise
 * estatística (mediana/jitter/perda) fica com o consumidor
 * ([io.signallq.app.feature.speedtest.AnalisadorAmostragemPing]), este módulo só mede.
 *
 * Esta é uma medição de ROTA REGIONAL DE REFERÊNCIA (mesmo destino/limite de promessa que o
 * LagCheck documenta), nunca o servidor oficial de um jogo específico — o chamador nunca deve
 * apresentar o resultado como "ping do jogo" (ver Architecture Plan, seção "Modo gamer —
 * medição real de rota...", riscos de produto).
 */
class SondaGameLiftBeacon(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 2_000,
) {
    private val random = SecureRandom()

    suspend fun sondar(amostras: Int): List<Double?> =
        withContext(Dispatchers.IO) {
            val sessionId = ByteArray(SESSION_ID_BYTES).also { random.nextBytes(it) }
            val address = InetSocketAddress(host, port)
            val socket = DatagramSocket().apply { soTimeout = timeoutMs }
            try {
                (0 until amostras).map { sequencia -> medirUmaAmostra(socket, address, sessionId, sequencia) }
            } finally {
                socket.close()
            }
        }

    private fun medirUmaAmostra(
        socket: DatagramSocket,
        address: InetSocketAddress,
        sessionId: ByteArray,
        sequencia: Int,
    ): Double? {
        val payload = construirPayload(sessionId, sequencia)
        val envio = DatagramPacket(payload, payload.size, address)
        val inicioNanos = System.nanoTime()
        return try {
            socket.send(envio)
            val bufferResposta = ByteArray(BUFFER_RESPOSTA_BYTES)
            val recebido = DatagramPacket(bufferResposta, bufferResposta.size)
            socket.receive(recebido)
            val rttMs = (System.nanoTime() - inicioNanos) / 1_000_000.0
            val respostaBytes = recebido.data.copyOfRange(recebido.offset, recebido.offset + recebido.length)
            if (respostaBytes.contentEquals(payload)) rttMs else null
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun construirPayload(
        sessionId: ByteArray,
        sequencia: Int,
    ): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val buffer = ByteBuffer.allocate(PAYLOAD_BYTES)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.put(VERSION)
        buffer.put(sessionId)
        buffer.putInt(sequencia)
        buffer.put(nonce)
        return buffer.array()
    }
}
