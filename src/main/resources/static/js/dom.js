// Tiny DOM helpers. Everything is built with createElement/textContent, never innerHTML,
// so server data can never be interpreted as markup (XSS-safe by construction).

export function h(tag, attrs = {}, ...kids) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
        if (value == null || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value);
        else el.setAttribute(key, value === true ? '' : value);
    }
    put(el, ...kids);
    return el;
}

/** Appends children, flattening nested arrays and skipping null/false (Element.append would print them as text). */
export function put(parent, ...kids) {
    for (const kid of kids.flat(Infinity)) {
        if (kid == null || kid === false) continue;
        parent.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
    }
    return parent;
}

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
export const money = (paise) => inr.format(paise / 100);
export const count = (n) => compact.format(n);

export const CATEGORIES = {
    MOBILES: ['Mobiles', '\u{1F4F1}'], LAPTOPS: ['Laptops', '\u{1F4BB}'], AUDIO: ['Audio & Headphones', '\u{1F3A7}'],
    SMARTWATCHES: ['Smartwatches', '⌚'], CAMERAS: ['Cameras', '\u{1F4F7}'],
    MENS_CLOTHING: ["Men's Clothing", '\u{1F454}'], WOMENS_CLOTHING: ["Women's Clothing", '\u{1F457}'],
    FOOTWEAR: ['Footwear', '\u{1F45F}'], WATCHES: ['Watches', '\u{1F570}️'], BAGS: ['Bags & Luggage', '\u{1F392}'],
    HOME_KITCHEN: ['Home & Kitchen', '\u{1F3E0}'], BEAUTY: ['Beauty & Grooming', '\u{1F484}'],
    SPORTS: ['Sports & Fitness', '\u{1F3CB}️'], BOOKS: ['Books', '\u{1F4DA}'],
};
export const categoryLabel = (key) => (CATEGORIES[key] ? CATEGORIES[key][0] : key);

/** Only same-origin /img/ paths are allowed as image sources. */
const safeImg = (url) => (typeof url === 'string' && url.startsWith('/img/') ? url : null);

/** Gradient tile with the department emoji, used when a product has no photo. */
export function art(p, size = '') {
    const emoji = CATEGORIES[p.category] ? CATEGORIES[p.category][1] : '\u{1F6CD}️';
    return h('div', { class: `art ${size}`, 'data-cat': p.category, role: 'img', 'aria-label': p.name },
        h('span', { class: 'art-emoji', 'aria-hidden': 'true' }, emoji),
        h('span', { class: 'art-brand' }, p.brand));
}

/** Product photo when available, otherwise the generated art tile. */
export function picture(p, size = '') {
    const src = safeImg(p.imageUrl);
    if (!src) return art(p, size);
    const wrap = h('div', { class: `photo ${size}` });
    const img = h('img', { src, alt: p.name, loading: 'lazy' });
    img.addEventListener('error', () => wrap.replaceChildren(art(p, size)), { once: true });
    wrap.append(img);
    return wrap;
}

export function rating(p) {
    return h('span', { class: 'rating' },
        h('span', { class: 'rating-badge', 'aria-label': `Rated ${p.rating.toFixed(1)} out of 5` },
            `${p.rating.toFixed(1)} ★`),
        h('span', { class: 'rating-count' }, `(${count(p.ratingCount)})`));
}

export function priceBlock(p, big = false) {
    return h('div', { class: 'price-block' + (big ? ' big' : '') },
        h('span', { class: 'price' }, money(p.pricePaise)),
        p.discountPercent > 0 ? h('s', { class: 'mrp' }, money(p.mrpPaise)) : null,
        p.discountPercent > 0 ? h('span', { class: 'off' }, `${p.discountPercent}% off`) : null);
}

export function toast(message, isError = false) {
    const box = document.getElementById('toasts');
    const el = h('div', { class: 'toast' + (isError ? ' error' : '') }, message);
    box.append(el);
    setTimeout(() => el.remove(), 3600);
}

export const clear = (el) => el.replaceChildren();

export function skeletonGrid(n = 8) {
    return h('div', { class: 'grid', 'aria-hidden': 'true' },
        Array.from({ length: n }, () => h('div', { class: 'card product skeleton' },
            h('div', { class: 'sk-img' }), h('div', { class: 'sk-line' }), h('div', { class: 'sk-line short' }))));
}
