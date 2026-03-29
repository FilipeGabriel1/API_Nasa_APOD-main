console.log("JS funcionando");

// elementos
const media = document.getElementById("media");
const title = document.getElementById("title");
const date = document.getElementById("date");
const desc = document.getElementById("desc");
const info = document.getElementById("info");
const fact = document.getElementById("fact");
const modal = document.getElementById("modal");

// botões
const btnHistory = document.getElementById("btn-history");
const btnAI = document.getElementById("btn-ai");
const btnAIGen = document.getElementById("btn-ai-gen");
const search = document.getElementById("search");

// banco fake para simular dados
const db = [
  {
    date: "2026-03-21",
    title: "Nebulosa Borboleta",
    explanation: "Uma nebulosa extremamente quente e brilhante.",
    url: "https://apod.nasa.gov/apod/image/1901/ButterflyNebula_Hubble_960.jpg",
    type: "Nebulosa",
    distance: "3800 anos-luz"
  },
  {
    date: "2026-03-20",
    title: "Galáxia Andrômeda",
    explanation: "A galáxia mais próxima da Via Láctea.",
    url: "https://apod.nasa.gov/apod/image/1901/AndromedaGalaxy.jpg",
    type: "Galáxia",
    distance: "2.5 milhões anos-luz"
  }
];

let history = [];

// render
function render(data) {
  media.innerHTML = `<img src="${data.url}">`;

  title.innerText = data.title;
  date.innerText = data.date;
  desc.innerText = data.explanation;

  info.innerHTML = `
    <h3>Dados</h3>
    <p>Tipo: ${data.type}</p>
    <p>Distância: ${data.distance}</p>
  `;

  fact.innerHTML = `
    <h3>Fato Curioso</h3>
    <p>Objeto raro no universo.</p>
  `;

  history.unshift(data);
}

// busca
search.addEventListener("change", () => {
  const found = db.find(item => item.date === search.value);

  if (found) {
    render(found);
  } else {
    alert("Data não encontrada");
  }
});

// botões
btnHistory.onclick = () => {
  let text = history.map(h => `${h.date} - ${h.title}`).join("<br>");
  showModal(`<h3>Histórico</h3>${text}`);
};

btnAI.onclick = () => {
  showModal(`<h3>IA Tradicional</h3><p>${desc.innerText}</p>`);
};

btnAIGen.onclick = () => {
  showModal(`<h3>IA Generativa</h3><p>Imagine esta cena em detalhes: ${desc.innerText}</p>`);
};

// modal
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

document.getElementById("btn-history").onclick = () => {
  alert("funcionou");
};
// inicial
render(db[0]);