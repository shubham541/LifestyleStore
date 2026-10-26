import { ApiError, api, setToken } from './api.js';
import { clear, h, money, picture, toast } from './dom.js';
import { go, refreshCart } from './store.js';

const app = () => document.getElementById('app');
const msg = (e) => (e instanceof ApiError ? e.message : 'Something went wrong');

// ---------- cart ----------

export async function cartView() {
    const root = app();
    const cart = await api('/cart');
    root.append(h('h1', {}, 'Shopping cart', cart.itemCount ? h('span', { class: 'muted small' }, ` (${cart.itemCount} item${cart.itemCount === 1 ? '' : 's'})`) : null));

    if (!cart.items.length) {
        root.append(h('div', { class: 'empty card' }, h('div', { class: 'empty-icon' }, '\u{1F6D2}'),
            h('h2', {}, 'Your cart is empty'), h('p', { class: 'muted' }, 'Looks like you have not added anything yet.'),
            h('a', { class: 'btn btn-primary btn-lg', href: '#/products' }, 'Start shopping')));
        return;
    }

    const change = async (productId, quantity) => {
        try {
            await api(`/cart/items/${encodeURIComponent(productId)}`, { method: 'PUT', body: { quantity } });
            go('#/cart');
        } catch (e) { toast(msg(e), true); }
    };

    const rows = cart.items.map((i) => {
        const stepper = h('div', { class: 'stepper', role: 'group', 'aria-label': `Quantity for ${i.name}` },
            h('button', { type: 'button', 'aria-label': 'Decrease quantity', disabled: i.quantity <= 1, onclick: () => change(i.productId, i.quantity - 1) }, '−'),
            h('span', { 'aria-live': 'polite' }, i.quantity),
            h('button', { type: 'button', 'aria-label': 'Increase quantity', disabled: i.quantity >= 10, onclick: () => change(i.productId, i.quantity + 1) }, '+'));
        const remove = h('button', { class: 'link-btn', type: 'button', onclick: async () => {
            try { await api(`/cart/items/${encodeURIComponent(i.productId)}`, { method: 'DELETE' }); go('#/cart'); } catch (e) { toast(msg(e), true); }
        } }, 'Remove');
        const link = `#/product/${encodeURIComponent(i.productId)}`;
        return h('article', { class: 'cart-row card' },
            h('a', { class: 'cart-thumb', href: link }, picture({ imageUrl: i.imageUrl, name: i.name, brand: i.brand, category: i.category })),
            h('div', { class: 'cart-info' },
                h('span', { class: 'brand' }, i.brand),
                h('h3', {}, h('a', { href: link }, i.name)),
                h('div', { class: 'price-block' }, h('span', { class: 'price' }, money(i.unitPricePaise)),
                    i.mrpPaise > i.unitPricePaise ? h('s', { class: 'mrp' }, money(i.mrpPaise)) : null),
                i.stock < i.quantity ? h('span', { class: 'stock-low' }, `Only ${i.stock} in stock`) : null,
                h('div', { class: 'cart-actions' }, stepper, remove)),
            h('strong', { class: 'cart-line' }, money(i.linePaise)));
    });

    const missing = Math.max(0, cart.freeShippingAbovePaise - cart.subtotalPaise);
    const progress = h('div', { class: 'progress', role: 'progressbar', 'aria-valuemin': 0, 'aria-valuemax': 100,
        'aria-valuenow': Math.min(100, Math.round(100 * cart.subtotalPaise / cart.freeShippingAbovePaise)) },
    h('span', {}));
    progress.firstChild.style.width = `${Math.min(100, (100 * cart.subtotalPaise) / cart.freeShippingAbovePaise)}%`;

    const place = h('button', { class: 'btn btn-primary btn-lg btn-block', type: 'button' }, 'Place order');
    place.addEventListener('click', async () => {
        place.disabled = true;
        try {
            await api('/orders', { method: 'POST' });
            toast('Order placed. Thank you!');
            await refreshCart();
            go('#/orders');
        } catch (e) { toast(msg(e), true); place.disabled = false; }
    });

    root.append(h('div', { class: 'cart-layout' },
        h('div', { class: 'cart-items' }, rows),
        h('aside', { class: 'summary card' },
            h('h2', {}, 'Order summary'),
            missing ? h('p', { class: 'small' }, `Add ${money(missing)} more for `, h('strong', {}, 'free delivery'))
                : h('p', { class: 'small ok' }, '✅ You get free delivery!'),
            progress,
            h('dl', {},
                h('dt', {}, 'Subtotal'), h('dd', {}, money(cart.subtotalPaise)),
                cart.savingsPaise ? [h('dt', { class: 'ok' }, 'You save'), h('dd', { class: 'ok' }, `− ${money(cart.savingsPaise)}`)] : null,
                h('dt', {}, 'Delivery'), h('dd', {}, cart.shippingPaise ? money(cart.shippingPaise) : 'FREE'),
                h('dt', { class: 'total' }, 'Total'), h('dd', { class: 'total' }, money(cart.totalPaise))),
            place,
            h('p', { class: 'muted small' }, 'Demo store: no payment is taken.'))));
}

// ---------- orders ----------

export async function ordersView() {
    const root = app();
    const orders = await api('/orders');
    root.append(h('h1', {}, 'Your orders'));
    if (!orders.length) {
        root.append(h('div', { class: 'empty card' }, h('div', { class: 'empty-icon' }, '\u{1F4E6}'),
            h('h2', {}, 'No orders yet'), h('a', { class: 'btn btn-primary', href: '#/products' }, 'Browse products')));
        return;
    }
    for (const o of orders) {
        root.append(h('article', { class: 'card order' },
            h('header', {},
                h('div', {}, h('strong', {}, `Order #${o.id.slice(0, 8).toUpperCase()}`),
                    h('div', { class: 'muted small' }, new Date(o.createdAt).toLocaleString())),
                h('span', { class: 'status' }, o.status)),
            h('ul', { class: 'order-items' }, o.items.map((i) => h('li', {},
                h('a', { href: `#/product/${encodeURIComponent(i.productId)}` }, i.name),
                h('span', { class: 'muted' }, ` × ${i.quantity}`), h('span', { class: 'grow' }),
                h('span', {}, money(i.unitPricePaise * i.quantity))))),
            h('div', { class: 'order-foot' },
                h('span', { class: 'muted small' }, `Delivering to ${o.shippingAddress}`),
                h('span', {}, o.shippingPaise ? `Delivery ${money(o.shippingPaise)} · ` : 'Free delivery · ',
                    h('strong', {}, `Total ${money(o.totalPaise)}`)))));
    }
}

// ---------- account ----------

export async function accountView() {
    const me = await api('/account');
    const form = h('form', { class: 'card form-card', novalidate: true },
        h('h2', {}, 'My account'),
        h('div', { class: 'profile' }, h('div', { class: 'avatar', 'aria-hidden': 'true' }, me.name.slice(0, 1).toUpperCase()),
            h('div', {}, h('strong', {}, me.name), h('div', { class: 'muted small' }, me.email),
                h('div', { class: 'muted small' }, `${me.address}, ${me.city}`))),
        h('h3', {}, 'Change password'),
        field('Current password', 'oldPassword', { type: 'password', autocomplete: 'current-password' }),
        field('New password (10-72 characters)', 'newPassword', { type: 'password', autocomplete: 'new-password' }),
        field('Confirm new password', 'confirm', { type: 'password', autocomplete: 'new-password' }),
        h('button', { class: 'btn btn-primary', type: 'submit' }, 'Update password'));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = Object.fromEntries(new FormData(form).entries());
        if (d.newPassword !== d.confirm) { toast('New passwords do not match', true); return; }
        try {
            await api('/account/password', { method: 'POST', body: { oldPassword: d.oldPassword, newPassword: d.newPassword } });
            form.reset();
            toast('Password updated');
        } catch (err) {
            const first = err.fieldErrors && Object.values(err.fieldErrors)[0];
            toast(first || msg(err), true);
        }
    });
    app().append(form);
}

// ---------- auth ----------

function field(label, name, { type = 'text', autocomplete, errors = {}, hint } = {}) {
    const id = `f-${name}`;
    return h('div', { class: 'field' },
        h('label', { for: id }, label),
        h('input', { id, name, type, autocomplete, required: true, 'aria-invalid': errors[name] ? 'true' : null,
            'aria-describedby': errors[name] ? `${id}-err` : null }),
        hint ? h('p', { class: 'muted small' }, hint) : null,
        errors[name] ? h('p', { class: 'field-error', id: `${id}-err` }, errors[name]) : null);
}

function authLayout(title, subtitle, form) {
    return h('div', { class: 'auth' },
        h('div', { class: 'auth-side' },
            h('h2', {}, 'Welcome to LifeStyle Store'),
            h('ul', {}, ['✨ Personal AI shopping assistant', '\u{1F69A} Free delivery above ₹499', '\u{1F512} Secure, private checkout',
                '↩️ 7-day easy returns'].map((t) => h('li', {}, t)))),
        h('div', { class: 'card form-card' }, h('h2', {}, title), h('p', { class: 'muted' }, subtitle), form));
}

export function loginView() {
    const form = h('form', { novalidate: true },
        field('Email', 'email', { type: 'email', autocomplete: 'username' }),
        field('Password', 'password', { type: 'password', autocomplete: 'current-password' }),
        h('button', { class: 'btn btn-primary btn-block', type: 'submit' }, 'Login'),
        h('p', { class: 'center' }, "Don't have an account? ", h('a', { href: '#/signup' }, 'Register')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            const token = await api('/auth/login', { method: 'POST', body: Object.fromEntries(new FormData(form).entries()) });
            setToken(token.accessToken);
            toast('Welcome back!');
            await refreshCart();
            go('#/');
        } catch (err) { toast(msg(err), true); }
    });
    app().append(authLayout('Login', 'Login to make a purchase.', form));
}

export function signupView(errors = {}) {
    const root = app();
    clear(root);
    const form = h('form', { novalidate: true },
        field('Full name', 'name', { autocomplete: 'name', errors }),
        field('Email', 'email', { type: 'email', autocomplete: 'email', errors }),
        field('Password', 'password', { type: 'password', autocomplete: 'new-password', errors, hint: '10 to 72 characters' }),
        field('Contact number', 'contact', { type: 'tel', autocomplete: 'tel', errors }),
        field('City', 'city', { autocomplete: 'address-level2', errors }),
        field('Address', 'address', { autocomplete: 'street-address', errors }),
        h('button', { class: 'btn btn-primary btn-block', type: 'submit' }, 'Create account'),
        h('p', { class: 'center' }, 'Already registered? ', h('a', { href: '#/login' }, 'Login')));
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = Object.fromEntries(new FormData(form).entries());
        try {
            await api('/auth/signup', { method: 'POST', body: d });
            const token = await api('/auth/login', { method: 'POST', body: { email: d.email, password: d.password } });
            setToken(token.accessToken);
            toast('Account created. Welcome!');
            await refreshCart();
            go('#/');
        } catch (err) {
            if (err.fieldErrors && Object.keys(err.fieldErrors).length) signupView(err.fieldErrors);
            else toast(msg(err), true);
        }
    });
    root.append(authLayout('Create account', 'It takes less than a minute.', form));
}
