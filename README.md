# LifestyleStore

Shopping is what you need ...!!

The front end of an innovative shopping website loaded with colorful gestures based on the use of HTML, CSS, and Bootstrap.

Live site: https://shubham541.github.io/LifestyleStore/

## Features

- Landing page with a banner and a "Shop Now" call to action
- Products page with a grid of cameras, watches and clothing items
- Sign up, login, change-password (settings) and cart pages
- Responsive layout using Bootstrap 3

This is a static front-end prototype: the forms are not wired to any backend, so sign up, login and cart do not persist or submit data.

## Tech stack

- HTML5, CSS
- Bootstrap 3 (bundled under `bootstrap/`) and jQuery

## Project structure

```
index.html      landing page
products.html   product listing
signup.html     sign-up form
login.html      login form
settings.html   change-password form
cart.html       cart page
style.css       custom styles
bootstrap/      Bootstrap CSS/JS and jQuery
img/            product and banner images
fyp.ipynb       separate Colab notebook (OpenCV/NumPy image-processing experiments)
```

## Running locally

No build step or dependencies are needed. Clone the repository and open `index.html` in a browser, or serve the folder with any static server, for example:

```
python -m http.server 8000
```

then visit http://localhost:8000.
