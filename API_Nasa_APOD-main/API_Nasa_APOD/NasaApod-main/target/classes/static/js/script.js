console.log("JS funcionando com API");

const API_APOD = "/apod";
const API_SEARCH = "/apod/search";

// elementos
const media = document.getElementById("media");
const title = document.getElementById("title");
const date = document.getElementById("date");
const desc = document.getElementById("desc");
const info = document.getElementById("info");
const fact = document.getElementById("fact");
const modal = document.getElementById("modal");
const datePicker = document.getElementById("date-picker");

// botões
const btnHistory = document.getElementById("btn-history");
const btnAI = document.getElementById("btn-ai");
const btnAIGen = document.getElementById("btn-ai-gen");
const search = document.getElementById("search");

const facts = [
  "A luz de algumas nebulosas leva milhares de anos para chegar ate aqui.",
  "A NASA publica diariamente uma nova APOD desde 1995.",
  "Mesmo no vacuo, a luz viaja a quase 300 mil km/s.",
  "A Via Lactea tem centenas de bilhoes de estrelas.",
  "Existem galaxias tao distantes que vemos o passado do universo nelas."
];

const history = [];
let ultimosBuscaItens = [];

function escapeHtml(value) {
  if (!value) {
    return "";
  }

  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function toDateOnly(dateTime) {
  if (!dateTime) {
    return "Data indisponivel";
  }

  return dateTime.includes("T") ? dateTime.split("T")[0] : dateTime;
}

function getRandomFact() {
  return facts[Math.floor(Math.random() * facts.length)];
}

function upsertHistory(item) {
  const index = history.findIndex((h) => h.title === item.title && h.date === item.date);
  if (index >= 0) {
    history.splice(index, 1);
  }

  history.unshift(item);
  if (history.length > 20) {
    history.pop();
  }
}

function renderMedia(url, titleText) {
  if (!url) {
    media.innerHTML = "<p>Midia indisponivel no momento.</p>";
    return;
  }

  const lowerUrl = url.toLowerCase();
  const isVideo = lowerUrl.includes("youtube.com") || lowerUrl.includes("youtu.be") || lowerUrl.endsWith(".mp4");

  if (isVideo) {
    let embedUrl = url;
    if (url.includes("watch?v=")) {
      embedUrl = url.replace("watch?v=", "embed/");
    }
    if (url.includes("youtu.be/")) {
      embedUrl = url.replace("youtu.be/", "www.youtube.com/embed/");
    }

    media.innerHTML = `<iframe src="${escapeHtml(embedUrl)}" title="${escapeHtml(titleText)}" allowfullscreen></iframe>`;
    return;
  }

  media.innerHTML = `<img src="${escapeHtml(url)}" alt="${escapeHtml(titleText)}">`;
}

function render(data) {
  renderMedia(data.url, data.title);

  title.innerText = data.title || "Sem titulo";
  date.innerText = data.date || "Data indisponivel";
  desc.innerText = data.explanation || "Sem descricao disponivel.";

  info.innerHTML = `
    <h3>Dados</h3>
    <p>Tipo: ${escapeHtml(data.type || "Astronomia")}</p>
    <p>Fonte: NASA APOD</p>
  `;

  fact.innerHTML = `
    <h3>Fato Curioso</h3>
    <p>${escapeHtml(getRandomFact())}</p>
  `;

  upsertHistory({
    date: data.date || "Data indisponivel",
    title: data.title || "Sem titulo"
  });
}

function showModal(content) {
  modal.innerHTML = `
    <div class="modal-content">
      ${content}
      <br><br>
      <button onclick="closeModal()">Fechar</button>
    </div>
  `;
  modal.classList.remove("hidden");
}

function closeModal() {
  modal.classList.add("hidden");
}

window.closeModal = closeModal;

async function carregarApod(dateValue) {
  const queryString = dateValue ? `?date=${encodeURIComponent(dateValue)}` : "";

  try {
    const response = await fetch(`${API_APOD}${queryString}`);
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Erro HTTP ${response.status}`);
    }

    const data = await response.json();
    const dataFormatada = dateValue || new Date().toISOString().split("T")[0];

    render({
      title: data.title,
      explanation: data.explanation,
      url: data.url,
      date: dataFormatada,
      type: "APOD"
    });
  } catch (error) {
    console.error(error);
    const mensagemErro = error.message || "Falha ao carregar APOD";
    showModal(`<h3>Erro</h3><p>${escapeHtml(mensagemErro)}</p>`);
  }
}

function renderizarItemBusca(index) {
  if (!ultimosBuscaItens || index < 0 || index >= ultimosBuscaItens.length) {
    return;
  }

  const item = ultimosBuscaItens[index];
  const tipoMidiaPortugues = traduzirTipoMidia(item.mediaType);
  
  render({
    title: item.title || "Sem titulo",
    explanation: item.description || "Sem descricao disponivel.",
    url: item.mediaUrl,
    date: toDateOnly(item.dateCreated),
    type: tipoMidiaPortugues || "Conteudo NASA"
  });
}

function traduzirTipoMidia(tipoIngles) {
  if (!tipoIngles) return "Conteudo NASA";
  
  const mapa = {
    "image": "Imagem",
    "video": "Vídeo",
    "audio": "Áudio",
    "collection": "Coleção",
    "image/jpeg": "Imagem JPEG",
    "image/png": "Imagem PNG",
    "image/gif": "Imagem GIF",
    "video/mp4": "Vídeo MP4",
    "audio/mpeg": "Áudio MPEG"
  };
  
  return mapa[tipoIngles.toLowerCase()] || tipoIngles;
}

async function buscarConteudo(query) {
  const termo = (query || "").trim();
  if (!termo) {
    return;
  }

  try {
    const response = await fetch(`${API_SEARCH}?query=${encodeURIComponent(termo)}&limit=5`);
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Erro HTTP ${response.status}`);
    }

    const itens = await response.json();
    if (!Array.isArray(itens) || itens.length === 0) {
      showModal(`<h3>Busca</h3><p>Nenhum resultado encontrado para <strong>${escapeHtml(termo)}</strong>.</p>`);
      return;
    }

    // Armazena os itens para acesso posterior
    ultimosBuscaItens = itens;

    const lista = itens
      .map((item, index) => {
        return `<li style="cursor: pointer; padding: 12px; margin: 8px 0; border: 2px solid transparent; border-radius: 6px; transition: all 0.3s ease;" onclick="renderizarItemBusca(${index})" onmouseover="this.style.borderColor='#007bff'; this.style.backgroundColor='#f8f9fa'; this.style.boxShadow='0 0 8px rgba(0, 123, 255, 0.3)'" onmouseout="this.style.borderColor='transparent'; this.style.backgroundColor='transparent'; this.style.boxShadow='none'">${escapeHtml(item.title || "Sem titulo")} (${escapeHtml(toDateOnly(item.dateCreated))})</li>`;
      })
      .join("");

    showModal(`
      <h3>Resultados para: ${escapeHtml(termo)}</h3>
      <p>Clique em qualquer resultado para visualizar na tela principal.</p>
      <ul style="list-style: none; padding: 0;">${lista}</ul>
    `);
  } catch (error) {
    console.error(error);
    const mensagemErro = traduzirMensagemErro(error.message || "Falha na busca");
    showModal(`<h3>Erro na busca</h3><p>${escapeHtml(mensagemErro)}</p>`);
  }
}

function traduzirMensagemErro(mensagemIngles) {
  const mapaErros = {
    "Bad Request": "Requisição inválida",
    "Unauthorized": "Não autorizado",
    "Forbidden": "Acesso proibido",
    "Not Found": "Não encontrado",
    "Internal Server Error": "Erro interno do servidor",
    "Service Unavailable": "Serviço indisponível",
    "Gateway Timeout": "Tempo limite excedido"
  };
  
  for (const [ingles, portugues] of Object.entries(mapaErros)) {
    if (mensagemIngles.includes(ingles)) {
      return mensagemIngles.replace(ingles, portugues);
    }
  }
  
  return mensagemIngles;
}

btnHistory.onclick = () => {
  if (history.length === 0) {
    showModal("<h3>Historico</h3><p>Nenhum item visualizado ainda.</p>");
    return;
  }

  const text = history
    .map((h) => `${escapeHtml(h.date)} - ${escapeHtml(h.title)}`)
    .join("<br>");

  showModal(`<h3>Historico</h3>${text}`);
};

btnAI.onclick = () => {
  showModal(`<h3>IA Tradicional</h3><p>${escapeHtml(desc.innerText || "Sem descricao para analisar.")}</p>`);
};

function renderChatMessage(role, content) {
  const safeContent = escapeHtml(content || "");
  const label = role === "user" ? "Você" : "IA";
  const roleClass = role === "user" ? "user" : "assistant";
  return `
    <div class="chat-message ${roleClass}">
      <div class="chat-message-label">${label}</div>
      <div class="chat-message-content">${safeContent}</div>
    </div>
  `;
}

function openGenerativeAiChatModal() {
  const contextoAtual = desc.innerText || "";

  showModal(`
    <h3>IA Generativa</h3>
    <p class="chat-hint">Pergunte algo. (Eu uso a descrição atual como contexto, quando existir.)</p>
    <div class="chat-box">
      <div id="chat-messages" class="chat-messages"></div>
      <div class="chat-input-row">
        <input id="chat-input" class="chat-input" type="text" placeholder="Escreva sua pergunta..." autocomplete="off" />
        <button id="chat-send" class="chat-send">Enviar</button>
      </div>
    </div>
  `);

  const messagesEl = document.getElementById("chat-messages");
  const inputEl = document.getElementById("chat-input");
  const sendBtn = document.getElementById("chat-send");

  if (!messagesEl || !inputEl || !sendBtn) {
    return;
  }

  function append(role, text) {
    messagesEl.insertAdjacentHTML("beforeend", renderChatMessage(role, text));
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  async function send() {
    const text = (inputEl.value || "").trim();
    if (!text) return;

    inputEl.value = "";
    append("user", text);

    sendBtn.disabled = true;
    inputEl.disabled = true;

    const thinkingId = `thinking-${Date.now()}`;
    messagesEl.insertAdjacentHTML(
      "beforeend",
      `<div id="${thinkingId}" class="chat-message assistant"><div class="chat-message-label">IA</div><div class="chat-message-content">Pensando...</div></div>`
    );
    messagesEl.scrollTop = messagesEl.scrollHeight;

    try {
      const response = await fetch("/ai/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          message: text,
          context: contextoAtual
        })
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(errText || `Erro HTTP ${response.status}`);
      }

      const data = await response.json();
      const reply = (data && data.reply) ? data.reply : "Sem resposta.";

      const thinkingEl = document.getElementById(thinkingId);
      if (thinkingEl) {
        thinkingEl.outerHTML = renderChatMessage("assistant", reply);
      } else {
        append("assistant", reply);
      }
    } catch (error) {
      console.error(error);
      const thinkingEl = document.getElementById(thinkingId);
      const msg = error.message || "Falha ao conversar com a IA.";
      const final = `Não consegui responder agora. ${msg}`;
      if (thinkingEl) {
        thinkingEl.outerHTML = renderChatMessage("assistant", final);
      } else {
        append("assistant", final);
      }
    } finally {
      sendBtn.disabled = false;
      inputEl.disabled = false;
      inputEl.focus();
    }
  }

  sendBtn.addEventListener("click", send);
  inputEl.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      send();
    }
  });

  append("assistant", "Olá! O que você quer saber?");
  inputEl.focus();
}

btnAIGen.onclick = () => {
  openGenerativeAiChatModal();
};

search.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    buscarConteudo(search.value);
  }
});

search.addEventListener("change", () => {
  buscarConteudo(search.value);
});

datePicker.addEventListener("change", () => {
  if (!datePicker.value) {
    carregarApod();
    return;
  }

  carregarApod(datePicker.value);
});

carregarApod();