// Netlify serverless function — proxies Google token exchange
// Keeps client_secret off the client (APK/browser)
exports.handler = async (event) => {
  if (event.httpMethod !== 'POST') {
    return { statusCode: 405, body: 'Method Not Allowed' };
  }

  const CORS = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
  };

  try {
    const body = JSON.parse(event.body || '{}');
    const { code, code_verifier, redirect_uri, refresh_token, grant_type } = body;

    const params = new URLSearchParams({
      client_id:     process.env.GOOGLE_CLIENT_ID,
      client_secret: process.env.GOOGLE_CLIENT_SECRET,
      grant_type:    grant_type || 'authorization_code',
    });

    if (grant_type === 'refresh_token') {
      if (!refresh_token) return { statusCode: 400, headers: CORS, body: JSON.stringify({ error: 'refresh_token required' }) };
      params.set('refresh_token', refresh_token);
    } else {
      if (!code) return { statusCode: 400, headers: CORS, body: JSON.stringify({ error: 'code required' }) };
      params.set('code', code);
      params.set('code_verifier', code_verifier || '');
      params.set('redirect_uri', redirect_uri || '');
    }

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });

    const data = await res.json();
    return {
      statusCode: res.status,
      headers: { ...CORS, 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    };
  } catch (e) {
    return { statusCode: 500, headers: CORS, body: JSON.stringify({ error: e.message }) };
  }
};
