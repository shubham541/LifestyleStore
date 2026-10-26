import { isLoggedIn, logout } from './api.js';
import { initAssistant } from './assistant.js';
import { homeView, productView, productsView } from './catalog.js';
import { clear, h, put, toast } from './dom.js';
import { accountView, cartView, loginView, ordersView, signupView } from './shop.js';
import { currentRoute, go, loadMeta, onChange, refreshCart, state } from './store.js';

const app = document.getElementById('app');
const nav = document.getElementById('nav');
const strip = document.getElementById('category-strip');
const searchForm = document.getElementById('search-form');
const searchInput = document.getElementById('search-input');

const guarded = (view) => () => {
    if (!isLoggedIn()) { toast('Please log in first', true); go('#/login'); return null; }
    return view();
};

const routes = {
    '/': homeView,
    '/products': productsView,
    '/cart': guarded(cartView),
    '/orders': guarded(ordersView),
    '/account': guarded(accountView),
    '/login': loginView,
    '/signup': signupView,
};

// ---------- header ----------

function renderNav() {
    const here = location.hash.split('?')[0] || '#/';
    const link = (href, label, extra) => h('a', { href, 'aria-current': here === href ? 'page' : null }, label, extra);
    clear(nav);
    nav.append(link('#/products', 'Shop'));
    if (isLoggedIn()) {
        nav.append(
            link('#/orders', 'Orders'),
            link('#/account', 'Account'),
            link('#/cart', '\u{1F6D2} Cart', state.cartCount ? h('span', { class: 'badge' }, state.cartCount) : null),
            h('button', { type: 'button', onclick: () => { logout(); state.cartCount = 0; toast('Logged out'); go('#/'); } }, 'Logout'));
    } else {
        nav.append(link('#/login', 'Login'), h('a', { class: 'btn btn-primary btn-sm', href: '#/signup' }, 'Sign up'));
    }
}

async function renderStrip() {
    try {
        const meta = await loadMeta();
        const active = currentRoute().params.get('category');
        clear(strip);
        put(strip, h('a', { href: '#/products' }, 'All'),
            meta.categories.map((c) => h('a', { href: `#/products?category=${c.key}`, 'aria-current': active === c.key ? 'true' : null },
                h('span', { 'aria-hidden': 'true' }, c.emoji), ` ${c.label}`)),
            h('a', { class: 'deal-link', href: '#/products?sort=DISCOUNT' }, '\u{1F525} Deals'));
    } catch { /* the strip is optional */ }
}

searchForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const q = searchInput.value.trim();
    go(q ? `#/products?q=${encodeURIComponent(q)}` : '#/products');
});

// ---------- router ----------

async function route() {
    const { path, params } = currentRoute();
    let view = routes[path];
    let arg;
    if (!view && path.startsWith('/product/')) {
        view = productView;
        arg = decodeURIComponent(path.slice('/product/'.length));
    }
    view = view || notFoundView;
    clear(app);
    if (path !== '/products') searchInput.value = '';
    else if (params.has('q')) searchInput.value = params.get('q');
    try {
        await view(arg);
    } catch (e) {
        clear(app);
        app.append(h('div', { class: 'empty card' }, h('div', { class: 'empty-icon' }, '\u{1F615}'),
            h('h2', {}, e && e.status === 404 ? 'We could not find that' : 'Something went wrong'),
            h('p', { class: 'muted' }, e && e.message ? e.message : 'Please try again.'),
            h('a', { class: 'btn btn-primary', href: '#/products' }, 'Back to shop')));
    }
    renderNav();
    renderStrip();
    if (path !== '/products') window.scrollTo({ top: 0 });
    app.focus({ preventScroll: true });
}

function notFoundView() {
    app.append(h('div', { class: 'empty card' }, h('h1', {}, 'Page not found'), h('a', { class: 'btn btn-primary', href: '#/' }, 'Back home')));
}

window.addEventListener('hashchange', route);
window.addEventListener('ls:auth', () => { state.cartCount = 0; renderNav(); go('#/login'); });
onChange(renderNav);

initAssistant();
refreshCart();
route();
