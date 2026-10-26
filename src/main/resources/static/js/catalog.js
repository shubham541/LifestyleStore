import { api, isLoggedIn } from './api.js';
import { openAssistant } from './assistant.js';
import { categoryLabel, clear, h, picture, priceBlock, put, rating, skeletonGrid, toast } from './dom.js';
import { addToCart, currentRoute, go, loadMeta } from './store.js';

const app = () => document.getElementById('app');

// ---------- shared pieces ----------

export function productCard(p) {
    const outOfStock = p.stock < 1;
    const add = h('button', { class: 'btn btn-primary btn-block', type: 'button', disabled: outOfStock },
        outOfStock ? 'Out of stock' : 'Add to cart');
    add.addEventListener('click', async () => {
        add.disabled = true;
        await addToCart(p);
        add.disabled = false;
    });
    return h('article', { class: 'card product' },
        h('a', { class: 'product-media', href: `#/product/${encodeURIComponent(p.id)}`, 'aria-label': p.name },
            picture(p), p.discountPercent >= 10 ? h('span', { class: 'ribbon' }, `${p.discountPercent}% off`) : null),
        h('div', { class: 'body' },
            h('span', { class: 'brand' }, p.brand),
            h('h3', {}, h('a', { href: `#/product/${encodeURIComponent(p.id)}` }, p.name)),
            rating(p),
            priceBlock(p),
            outOfStock ? null : p.stock <= 5 ? h('span', { class: 'stock-low' }, `Only ${p.stock} left`) : null,
            add));
}

const productGrid = (items) => h('div', { class: 'grid' }, items.map(productCard));

function section(title, subtitle, link, content) {
    return h('section', { class: 'section' },
        h('div', { class: 'section-head' },
            h('div', {}, h('h2', {}, title), subtitle ? h('p', { class: 'muted' }, subtitle) : null),
            link ? h('a', { class: 'link-more', href: link }, 'View all →') : null),
        content);
}

// ---------- home ----------

export async function homeView() {
    const root = app();
    const [meta, deals, topRated, popular] = await Promise.all([
        loadMeta(),
        api('/products?sort=DISCOUNT&inStock=true&size=4'),
        api('/products?sort=RATING&minRating=4.5&size=4'),
        api('/products?sort=RELEVANCE&size=8'),
    ]);

    const ask = h('button', { class: 'btn btn-light btn-lg', type: 'button' }, '✨ Ask our AI assistant');
    ask.addEventListener('click', () => openAssistant());

    const tiles = meta.categories.map((c) =>
        h('a', { class: 'tile', href: `#/products?category=${c.key}`, 'data-cat': c.key },
            h('span', { class: 'tile-emoji', 'aria-hidden': 'true' }, c.emoji),
            h('strong', {}, c.label), h('span', { class: 'muted' }, `${c.count} products`)));

    root.append(
        h('section', { class: 'hero' },
            h('div', { class: 'hero-copy' },
                h('span', { class: 'eyebrow' }, 'New season · Up to 60% off'),
                h('h1', {}, 'Everything you love, one lifestyle store.'),
                h('p', {}, 'Phones, laptops, fashion, home and more at honest prices, with an AI assistant that helps you choose.'),
                h('div', { class: 'hero-cta' },
                    h('a', { class: 'btn btn-primary btn-lg', href: '#/products' }, 'Shop now'), ask)),
            h('div', { class: 'hero-art', 'aria-hidden': 'true' },
                ['\u{1F4F1}', '\u{1F3A7}', '\u{1F45F}', '⌚', '\u{1F4F7}', '\u{1F392}'].map((e) => h('span', {}, e)))),
        h('ul', { class: 'perks' },
            [['\u{1F69A}', 'Free delivery', 'On orders above ₹499'], ['↩️', '7-day returns', 'No questions asked'],
                ['\u{1F512}', 'Secure checkout', 'Your data stays safe'], ['✨', 'AI shopping help', 'Ask anything, anytime']]
                .map(([i, t, s]) => h('li', {}, h('span', { class: 'perk-icon', 'aria-hidden': 'true' }, i),
                    h('div', {}, h('strong', {}, t), h('span', { class: 'muted' }, s))))),
        section('Shop by department', 'Browse everything we sell', null, h('div', { class: 'tiles' }, tiles)),
        section('Deals of the day', 'The biggest discounts right now', '#/products?sort=DISCOUNT', productGrid(deals.items)),
        section('Top rated', 'Loved by customers: 4.5 stars and above', '#/products?sort=RATING&minRating=4.5',
            productGrid(topRated.items)),
        section('Popular right now', null, '#/products', productGrid(popular.items)));
}

// ---------- listing ----------

const SORTS = [['RELEVANCE', 'Popularity'], ['PRICE_ASC', 'Price: low to high'], ['PRICE_DESC', 'Price: high to low'],
    ['RATING', 'Customer rating'], ['DISCOUNT', 'Biggest discount']];
const PRICE_PRESETS = [[0, 1000, 'Under ₹1,000'], [1000, 5000, '₹1,000 - ₹5,000'],
    [5000, 25000, '₹5,000 - ₹25,000'], [25000, '', 'Over ₹25,000']];

export async function productsView() {
    const root = app();
    const meta = await loadMeta();
    const { params } = currentRoute();
    const f = {
        category: params.get('category') || '', q: params.get('q') || '', minPrice: params.get('minPrice') || '',
        maxPrice: params.get('maxPrice') || '', minRating: params.get('minRating') || '', brand: params.get('brand') || '',
        inStock: params.get('inStock') === 'true', sort: params.get('sort') || 'RELEVANCE',
        page: Number(params.get('page') || 0) || 0,
    };

    const sidebar = h('aside', { class: 'filters card' });
    const heading = h('h1', {});
    const bar = h('div', { class: 'results-bar' });
    const active = h('div', { class: 'chips' });
    const results = h('div', { 'aria-live': 'polite' });
    const pager = h('nav', { class: 'pager', 'aria-label': 'Pagination' });

    const query = () => {
        const p = new URLSearchParams();
        for (const [k, v] of Object.entries(f)) {
            if (v === '' || v === false || (k === 'page' && !v) || (k === 'sort' && v === 'RELEVANCE')) continue;
            p.set(k, String(v));
        }
        return p;
    };

    function update(partial, resetPage = true) {
        Object.assign(f, partial);
        if (resetPage && !('page' in partial)) f.page = 0;
        history.replaceState(null, '', `#/products${query().toString() ? `?${query()}` : ''}`);
        drawSidebar();
        load();
    }

    function drawSidebar() {
        clear(sidebar);
        const radio = (label, checked, onpick) => h('label', { class: 'opt' },
            h('input', { type: 'radio', name: 'cat', checked, onchange: onpick }), label);

        const cats = [radio('All departments', f.category === '', () => update({ category: '', brand: '' })),
            meta.categories.map((c) => radio(`${c.emoji} ${c.label} (${c.count})`, f.category === c.key,
                () => update({ category: c.key, brand: '' })))];

        const current = meta.categories.find((c) => c.key === f.category);
        const brands = [...new Set((current ? current.brands : meta.categories.flatMap((c) => c.brands)))]
            .sort((a, b) => a.localeCompare(b));
        const brandSelect = h('select', { 'aria-label': 'Brand', onchange: (e) => update({ brand: e.target.value }) },
            h('option', { value: '' }, 'All brands'),
            brands.map((b) => h('option', { value: b, selected: b === f.brand }, b)));

        const min = h('input', { type: 'number', min: 0, placeholder: 'Min', value: f.minPrice, 'aria-label': 'Minimum price' });
        const max = h('input', { type: 'number', min: 0, placeholder: 'Max', value: f.maxPrice, 'aria-label': 'Maximum price' });
        const applyPrice = () => update({ minPrice: min.value, maxPrice: max.value });
        min.addEventListener('change', applyPrice);
        max.addEventListener('change', applyPrice);

        sidebar.append(
            h('div', { class: 'filter-group' }, h('h3', {}, 'Department'), h('div', { class: 'scroll' }, cats)),
            h('div', { class: 'filter-group' }, h('h3', {}, 'Price (₹)'),
                h('div', { class: 'range' }, min, h('span', {}, 'to'), max),
                h('div', { class: 'presets' }, PRICE_PRESETS.map(([lo, hi, label]) =>
                    h('button', { class: 'chip', type: 'button',
                        onclick: () => update({ minPrice: String(lo || ''), maxPrice: String(hi) }) }, label)))),
            h('div', { class: 'filter-group' }, h('h3', {}, 'Customer rating'),
                [['', 'Any'], ['4', '4★ & above'], ['3', '3★ & above']].map(([v, label]) =>
                    h('label', { class: 'opt' }, h('input', { type: 'radio', name: 'rating', checked: f.minRating === v,
                        onchange: () => update({ minRating: v }) }), label))),
            h('div', { class: 'filter-group' }, h('h3', {}, 'Brand'), brandSelect),
            h('div', { class: 'filter-group' },
                h('label', { class: 'opt' }, h('input', { type: 'checkbox', checked: f.inStock,
                    onchange: (e) => update({ inStock: e.target.checked }) }), 'In stock only')));
    }

    function drawChips() {
        clear(active);
        const chip = (label, clearer) => active.append(h('button', { class: 'chip chip-active', type: 'button',
            onclick: () => update(clearer), 'aria-label': `Remove filter ${label}` }, `${label} ×`));
        if (f.q) chip(`"${f.q}"`, { q: '' });
        if (f.category) chip(categoryLabel(f.category), { category: '', brand: '' });
        if (f.brand) chip(f.brand, { brand: '' });
        if (f.minPrice || f.maxPrice) chip(`₹${f.minPrice || 0} - ${f.maxPrice ? `₹${f.maxPrice}` : 'any'}`, { minPrice: '', maxPrice: '' });
        if (f.minRating) chip(`${f.minRating}★ & above`, { minRating: '' });
        if (f.inStock) chip('In stock', { inStock: false });
    }

    let token = 0;
    async function load() {
        const mine = ++token;
        heading.textContent = f.q ? `Results for "${f.q}"` : f.category ? categoryLabel(f.category) : 'All products';
        clear(results);
        results.append(skeletonGrid(8));
        const p = query();
        p.set('size', '12');
        if (!p.has('page')) p.set('page', '0');
        try {
            const data = await api(`/products?${p}`);
            if (mine !== token) return;
            clear(results);
            clear(bar);
            bar.append(h('span', { class: 'muted' }, `${data.totalItems} result${data.totalItems === 1 ? '' : 's'}`),
                h('label', { class: 'sort' }, 'Sort by ',
                    h('select', { onchange: (e) => update({ sort: e.target.value }) },
                        SORTS.map(([v, label]) => h('option', { value: v, selected: v === f.sort }, label)))));
            drawChips();
            results.append(data.items.length ? productGrid(data.items)
                : h('div', { class: 'empty' }, h('div', { class: 'empty-icon' }, '\u{1F50D}'),
                    h('h2', {}, 'No products match'), h('p', { class: 'muted' }, 'Try removing a filter or searching for something else.'),
                    h('button', { class: 'btn btn-primary', type: 'button', onclick: () => update({ category: '', q: '', brand: '', minPrice: '', maxPrice: '', minRating: '', inStock: false }) }, 'Clear all filters')));
            drawPager(data);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } catch (e) {
            if (mine === token) { clear(results); results.append(h('p', { class: 'empty' }, e.message)); }
        }
    }

    function drawPager(data) {
        clear(pager);
        if (data.totalPages < 2) return;
        const go = (page) => update({ page }, false);
        const pages = [];
        for (let i = 0; i < data.totalPages; i++) {
            if (i === 0 || i === data.totalPages - 1 || Math.abs(i - data.page) <= 1) pages.push(i);
            else if (pages[pages.length - 1] !== '…') pages.push('…');
        }
        put(pager,
            h('button', { class: 'btn', type: 'button', disabled: data.page === 0, onclick: () => go(data.page - 1) }, '← Prev'),
            pages.map((i) => i === '…' ? h('span', { class: 'muted' }, '…')
                : h('button', { class: 'btn' + (i === data.page ? ' btn-primary' : ''), type: 'button',
                    'aria-current': i === data.page ? 'page' : null, onclick: () => go(i) }, i + 1)),
            h('button', { class: 'btn', type: 'button', disabled: data.page + 1 >= data.totalPages, onclick: () => go(data.page + 1) }, 'Next →'));
    }

    const toggle = h('button', { class: 'btn filters-toggle', type: 'button', 'aria-expanded': 'false' }, 'Filters');
    toggle.addEventListener('click', () => {
        const open = sidebar.classList.toggle('open');
        toggle.setAttribute('aria-expanded', String(open));
    });

    drawSidebar();
    root.append(heading, h('div', { class: 'listing' }, sidebar,
        h('section', {}, h('div', { class: 'mobile-bar' }, toggle), bar, active, results, pager)));
    await load();
}

// ---------- product detail ----------

export async function productView(id) {
    const root = app();
    const p = await api(`/products/${encodeURIComponent(id)}`);
    let related = [];
    try { related = await api(`/products/${encodeURIComponent(id)}/related`); } catch { /* optional */ }

    const qty = h('select', { 'aria-label': 'Quantity' }, Array.from({ length: Math.min(10, Math.max(p.stock, 1)) },
        (_, i) => h('option', { value: i + 1 }, i + 1)));
    const soldOut = p.stock < 1;
    const add = h('button', { class: 'btn btn-outline btn-lg', type: 'button', disabled: soldOut }, 'Add to cart');
    const buy = h('button', { class: 'btn btn-primary btn-lg', type: 'button', disabled: soldOut }, 'Buy now');
    add.addEventListener('click', async () => { add.disabled = true; await addToCart(p, Number(qty.value)); add.disabled = soldOut; });
    buy.addEventListener('click', async () => { if (await addToCart(p, Number(qty.value))) go('#/cart'); });

    const ask = h('button', { class: 'btn btn-ai', type: 'button' }, '✨ Ask AI about this product');
    ask.addEventListener('click', () => {
        if (!isLoggedIn()) { toast('Log in to use the AI assistant', true); go('#/login'); return; }
        openAssistant({ product: p, prompt: 'Is this a good buy? Give me the pros, cons and how it compares with similar options.' });
    });

    put(root,
        h('nav', { class: 'crumbs', 'aria-label': 'Breadcrumb' }, h('a', { href: '#/' }, 'Home'), ' / ',
            h('a', { href: `#/products?category=${p.category}` }, categoryLabel(p.category)), ' / ', h('span', {}, p.name)),
        h('div', { class: 'detail' },
            h('div', { class: 'detail-media card' }, picture(p, 'large'),
                p.imageNote ? h('p', { class: 'img-note muted small' }, p.imageNote) : null),
            h('div', { class: 'detail-info' },
                h('a', { class: 'brand', href: `#/products?brand=${encodeURIComponent(p.brand)}` }, p.brand),
                h('h1', {}, p.name),
                rating(p),
                priceBlock(p, true),
                h('p', { class: 'muted small' }, 'Inclusive of all taxes. Free delivery on orders above ₹499.'),
                soldOut ? h('p', { class: 'stock-low' }, 'Currently out of stock')
                    : h('p', { class: p.stock <= 5 ? 'stock-low' : 'in-stock' }, p.stock <= 5 ? `Hurry, only ${p.stock} left` : 'In stock'),
                h('p', { class: 'description' }, p.description),
                h('div', { class: 'tags' }, p.tags.map((t) => h('a', { class: 'chip', href: `#/products?q=${encodeURIComponent(t)}` }, t))),
                h('div', { class: 'buy-row' }, h('label', { class: 'qty-label' }, 'Qty ', qty), add, buy),
                ask,
                h('ul', { class: 'assurances' },
                    ['\u{1F69A} Delivered in 2-5 days', '↩️ 7-day easy returns', '\u{1F6E1}️ 1-year brand warranty'].map((t) => h('li', {}, t))))),
        related.length ? section('You may also like', `More in ${categoryLabel(p.category)}`, `#/products?category=${p.category}`, productGrid(related)) : null);
}
