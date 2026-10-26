import { ApiError, api, isLoggedIn } from './api.js';
import { toast } from './dom.js';

/** Tiny shared state + navigation helpers used by every view. */
export const state = { cartCount: 0, meta: null };

const listeners = new Set();
export const onChange = (fn) => listeners.add(fn);
const emit = () => listeners.forEach((fn) => fn());

export function go(hash) {
    if (location.hash === hash) window.dispatchEvent(new HashChangeEvent('hashchange'));
    else location.hash = hash;
}

export function currentRoute() {
    const raw = location.hash.replace(/^#/, '') || '/';
    const [path, query = ''] = raw.split('?');
    return { path, params: new URLSearchParams(query) };
}

export async function loadMeta() {
    if (!state.meta) state.meta = await api('/products/meta');
    return state.meta;
}

export async function refreshCart() {
    if (!isLoggedIn()) state.cartCount = 0;
    else {
        try { state.cartCount = (await api('/cart')).itemCount; } catch { state.cartCount = 0; }
    }
    emit();
}

/** Used by product cards, the product page and the AI assistant. Returns true when the item was added. */
export async function addToCart(product, quantity = 1) {
    if (!isLoggedIn()) {
        toast('Log in to add items to your cart', true);
        go('#/login');
        return false;
    }
    try {
        await api('/cart/items', { method: 'POST', body: { productId: product.id, quantity } });
        toast(`${product.name} added to cart`);
        await refreshCart();
        return true;
    } catch (e) {
        toast(e instanceof ApiError ? e.message : 'Could not add to cart', true);
        return false;
    }
}
