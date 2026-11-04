package io.signallq.app.assist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.signallq.app.assist.model.AssistContextoRede
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistChatScreen(
    contexto: AssistContextoRede?,
    onFechar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var textoDigitado by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(contexto) {
        viewModel.inicializarContexto(contexto)
    }

    // Auto-scroll para a última mensagem quando novas chegarem ou o streaming atualizar
    LaunchedEffect(
        uiState.mensagens.size,
        uiState.mensagens
            .lastOrNull()
            ?.texto
            ?.length,
    ) {
        if (uiState.mensagens.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(uiState.mensagens.size - 1)
        }
    }

    // Auto-scroll quando o teclado abre para não cobrir a última mensagem
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && uiState.mensagens.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(uiState.mensagens.size - 1)
        }
    }

    BackHandler {
        viewModel.limparSessao()
        onFechar()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SignallQ Assist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.limparSessao()
                            onFechar()
                        },
                        modifier = Modifier.semantics { contentDescription = "Fechar Assistente" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Assistente",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        bottomBar = {
            BarraDigitacaoAssist(
                texto = textoDigitado,
                onTextoChange = { textoDigitado = it },
                onEnviar = {
                    val prompt = textoDigitado.trim()
                    if (prompt.isNotBlank()) {
                        viewModel.enviarMensagem(prompt)
                        textoDigitado = ""
                    }
                },
                onCancelar = viewModel::cancelarGeracao,
                estaProcessando = uiState.estaProcessando,
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .imePadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            if (uiState.mensagens.isEmpty()) {
                item(key = "header_saudacao") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Como posso ajudar?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item(key = "card_medicao") {
                CardUltimaMedicao(contexto = uiState.contextoRede)
                if (uiState.contextoRede != null && !uiState.contextoRede!!.semMedicao) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.mensagens.isEmpty()) {
                item(key = "chips_perguntas") {
                    ChipsPerguntasFrequentes(
                        perguntas = uiState.perguntasSugeridas,
                        onPerguntaClick = { pergunta ->
                            viewModel.enviarMensagem(pergunta)
                        },
                        habilitado = !uiState.estaProcessando,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(
                items = uiState.mensagens,
                key = { it.id },
            ) { mensagem ->
                BalaoMensagem(
                    mensagem = mensagem,
                    onTentarNovamente = viewModel::tentarNovamente,
                )
            }

            item(key = "espacador_fim") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
