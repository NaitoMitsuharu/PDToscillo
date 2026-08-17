const variant = new URLSearchParams(location.search).get('variant') || 'product-pv';
await import(variant === 'product-pv' ? './product-pv.js' : './app.js');
