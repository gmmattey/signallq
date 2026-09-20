package io.signallq.app.core.probejogo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * Testes herméticos — servidor UDP falso em loopback, nunca contra a AWS real (a validação de
 * rede real fica com Breno em device físico, ver Architecture Plan).
 */
class SondaGameLiftBeaconTest {
    @Test
    fun `eco integral produz RTT nao nulo para cada amostra`() =
        runBlocking {
            val servidorFake = DatagramSocket(0)
            val porta = servidorFake.localPort
            val servidorJob =
                async(Dispatchers.IO) {
                    repeat(3) {
                        val buffer = ByteArray(128)
                        val pacote = DatagramPacket(buffer, buffer.size)
                        servidorFake.receive(pacote)
                        // Eco integral, como o beacon real da AWS GameLift.
                        val resposta = DatagramPacket(pacote.data, pacote.offset, pacote.length, pacote.address, pacote.port)
                        servidorFake.send(resposta)
                    }
                }

            val sonda = SondaGameLiftBeacon(host = "127.0.0.1", port = porta, timeoutMs = 1_000)
            val amostras = sonda.sondar(3)
            servidorJob.await()
            servidorFake.close()

            assertEquals(3, amostras.size)
            amostras.forEach { rtt ->
                assertTrue(rtt != null && rtt >= 0.0)
            }
        }

    @Test
    fun `destino sem ninguem escutando produz timeout, amostra nula, nunca sucesso fabricado`() =
        runBlocking {
            // Porta local reservada mas sem listener -- ICMP port-unreachable ou timeout, nunca RTT.
            val socketReservador = DatagramSocket(0)
            val portaLivre = socketReservador.localPort
            socketReservador.close()

            val sonda = SondaGameLiftBeacon(host = "127.0.0.1", port = portaLivre, timeoutMs = 300)
            val amostras = sonda.sondar(2)

            assertEquals(2, amostras.size)
            amostras.forEach { assertNull(it) }
        }

    @Test
    fun `resposta com bytes diferentes do payload enviado nunca conta como amostra valida`() =
        runBlocking {
            val servidorFake = DatagramSocket(0)
            val porta = servidorFake.localPort
            val servidorJob =
                async(Dispatchers.IO) {
                    val buffer = ByteArray(128)
                    val pacote = DatagramPacket(buffer, buffer.size)
                    servidorFake.receive(pacote)
                    // Responde com payload alterado -- nunca deve ser aceito como eco válido.
                    val corrompido = pacote.data.copyOfRange(pacote.offset, pacote.offset + pacote.length)
                    corrompido[0] = (corrompido[0] + 1).toByte()
                    val resposta = DatagramPacket(corrompido, corrompido.size, pacote.address, pacote.port)
                    servidorFake.send(resposta)
                }

            val sonda = SondaGameLiftBeacon(host = "127.0.0.1", port = porta, timeoutMs = 1_000)
            val amostras = sonda.sondar(1)
            servidorJob.await()
            servidorFake.close()

            assertEquals(1, amostras.size)
            assertNull(amostras.first())
        }

    @Test
    fun `cada amostra usa payload proprio -- sequencias distintas nunca colidem`() =
        runBlocking {
            val servidorFake = DatagramSocket(0)
            val porta = servidorFake.localPort
            val payloadsRecebidos = mutableListOf<ByteArray>()
            val servidorJob =
                async(Dispatchers.IO) {
                    repeat(4) {
                        val buffer = ByteArray(128)
                        val pacote = DatagramPacket(buffer, buffer.size)
                        servidorFake.receive(pacote)
                        payloadsRecebidos.add(pacote.data.copyOfRange(pacote.offset, pacote.offset + pacote.length))
                        val resposta = DatagramPacket(pacote.data, pacote.offset, pacote.length, pacote.address, pacote.port)
                        servidorFake.send(resposta)
                    }
                }

            val sonda = SondaGameLiftBeacon(host = "127.0.0.1", port = porta, timeoutMs = 1_000)
            sonda.sondar(4)
            servidorJob.await()
            servidorFake.close()

            val payloadsUnicos = payloadsRecebidos.map { it.toList() }.toSet()
            assertEquals(4, payloadsUnicos.size)
        }
}
