import { ApiError, api, isLoggedIn } from './api.js';
import { clear, h, money, picture, rating } from './dom.js';
import { addToCart } from './store.js';

/**
 * Floating AI shopping assistant ("Lifey"). The conversation lives in memory and is re-sent on every turn (the
 * server is stateless). Product cards in the chat come from the server after it re-validated the ids; "Add to
 * cart" is always the customer's own click.
 */

const MAX_TURNS = 11; // history sent to the server; the server accepts at most 12
const STARTERS = ['Best rated phones under ₹30,000', 'Show me today’s best deals', 'Gift ideas under ₹2,000',
    'Compare the Sony and JBL headphones'];

let panel; let log; let input; let sendBtn; let contextBar; let sourceTag;
let open = false; let busy = false;
const turns = [];
let context = null;

export function initAssistant() {
    const toggle = h('button', { id: 'assistant-toggle', class: 'fab', type: 'button', 'aria-expanded': 'false', 'aria-controls': 'assistant' },
        h('span', { 'aria-hidden': 'true' }, '✨'), ' Ask AI');
    toggle.addEventListener('click', () => setOpen(!open));

    log = h('div', { class: 'assistant-log', 'aria-live': 'polite' });
    contextBar = h('div', { class: 'ctx', hidden: true });
    sourceTag = h('span', { class: 'src muted small' }, '');
    input = h('input', { type: 'text', maxlength: 500, required: true, placeholder: 'Ask about products, budgets, comparisons…',
        'aria-label': 'Your question', autocomplete: 'off' });
    sendBtn = h('button', { class: 'btn btn-primary', type: 'submit' }, 'Send');
    const form = h('form', { class: 'assistant-form' }, input, sendBtn);
    form.addEventListener('submit', (e) => { e.preventDefault(); send(input.value); });

    const reset = h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Start a new chat', title: 'New chat' }, '↻');
    reset.addEventListener('click', () => { turns.length = 0; context = null; renderWelcome(); });
    const close = h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Close assistant' }, '×');
    close.addEventListener('click', () => setOpen(false));

    panel = h('section', { id: 'assistant', class: 'assistant', hidden: true, 'aria-label': 'AI shopping assistant' },
        h('header', {}, h('div', { class: 'who' }, h('span', { class: 'bot-avatar', 'aria-hidden': 'true' }, '✨'),
            h('div', {}, h('strong', {}, 'Lifey'), h('div', {}, sourceTag))), h('div', {}, reset, close)),
        contextBar, log, form,
        h('p', { class: 'disclaimer muted small' }, 'AI can make mistakes. Check prices and details on the product page.'));

    document.body.append(toggle, panel);
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && open) setOpen(false); });
    renderWelcome();
}

function setOpen(value) {
    open = value;
    panel.hidden = !value;
    document.getElementById('assistant-toggle').setAttribute('aria-expanded', String(value));
    if (value) input.focus();
}

/** Open the panel, optionally anchored to a product and with a question pre-sent. */
export function openAssistant({ product = null, prompt = null } = {}) {
    if (!isLoggedIn()) {
        setOpen(true);
        renderLoginPrompt();
        return;
    }
    if (product) { context = product; turns.length = 0; renderWelcome(); }
    setOpen(true);
    if (prompt) send(prompt);
}

function renderContext() {
    clear(contextBar);
    contextBar.hidden = !context;
    if (!context) return;
    const drop = h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Stop using this product as context' }, '×');
    drop.addEventListener('click', () => { context = null; renderContext(); });
    contextBar.append(h('span', {}, 'Asking about: ', h('strong', {}, context.name)), drop);
}

function renderWelcome() {
    clear(log);
    renderContext();
    sourceTag.textContent = '';
    const starters = context
        ? ['Is this worth buying?', 'Show cheaper alternatives', 'What are the alternatives with better ratings?']
        : STARTERS;
    log.append(bubble('bot', context ? `Hi! Ask me anything about the ${context.name}.`
        : 'Hi, I’m Lifey! Tell me what you’re looking for, your budget, or ask me to compare products.'),
    chips(starters));
}

function renderLoginPrompt() {
    clear(log);
    log.append(bubble('bot', 'Please log in to chat with the shopping assistant.'),
        h('div', { class: 'actions' }, h('a', { class: 'btn btn-primary', href: '#/login', onclick: () => setOpen(false) }, 'Login'),
            h('a', { class: 'btn', href: '#/signup', onclick: () => setOpen(false) }, 'Create account')));
}

const bubble = (who, text) => h('div', { class: `msg ${who}` }, text);

function chips(list) {
    const box = h('div', { class: 'suggest' });
    for (const text of list) {
        const b = h('button', { class: 'chip', type: 'button' }, text);
        b.addEventListener('click', () => send(text));
        box.append(b);
    }
    return box;
}

function miniCard(p) {
    const add = h('button', { class: 'btn btn-primary btn-sm', type: 'button' }, 'Add');
    add.addEventListener('click', async () => { add.disabled = true; await addToCart(p); add.disabled = false; });
    const link = h('a', { href: `#/product/${encodeURIComponent(p.id)}`, class: 'mini-name' }, p.name);
    link.addEventListener('click', () => { if (window.matchMedia('(max-width: 640px)').matches) setOpen(false); });
    return h('div', { class: 'mini' },
        h('div', { class: 'mini-thumb' }, picture(p)),
        h('div', { class: 'mini-info' }, link,
            h('div', { class: 'mini-meta' }, h('strong', {}, money(p.pricePaise)),
                p.discountPercent > 0 ? h('span', { class: 'off' }, `${p.discountPercent}% off`) : null), rating(p)),
        add);
}

async function send(text) {
    text = String(text || '').trim();
    if (!text || busy) return;
    if (!isLoggedIn()) { renderLoginPrompt(); return; }
    if (!turns.length) clear(log);
    input.value = '';
    turns.push({ role: 'user', content: text.slice(0, 500) });
    log.append(bubble('user', text));
    const typing = h('div', { class: 'msg bot typing', 'aria-label': 'Lifey is typing' }, h('span', {}), h('span', {}), h('span', {}));
    log.append(typing);
    log.scrollTop = log.scrollHeight;
    busy = true; sendBtn.disabled = true;

    try {
        const body = { messages: turns.slice(-MAX_TURNS) };
        if (context) body.productId = context.id;
        const reply = await api('/assistant/chat', { method: 'POST', body });
        typing.remove();
        turns.push({ role: 'assistant', content: reply.reply.slice(0, 500) });
        const msg = bubble('bot', reply.reply);
        if (reply.products.length) msg.append(h('div', { class: 'minis' }, reply.products.map(miniCard)));
        log.append(msg);
        if (reply.suggestions && reply.suggestions.length) log.append(chips(reply.suggestions));
        sourceTag.textContent = reply.source === 'claude' ? 'Powered by Claude' : 'Smart search (AI offline)';
    } catch (e) {
        typing.remove();
        turns.pop(); // let the customer retry the same question
        log.append(bubble('bot', e instanceof ApiError ? e.message : 'Sorry, I could not reach the assistant.'));
    } finally {
        busy = false; sendBtn.disabled = false;
        log.scrollTop = log.scrollHeight;
        input.focus();
    }
}
