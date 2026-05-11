// In-memory access token store — never touches localStorage/sessionStorage.
// Keeps the access token out of reach of XSS attacks.
// The refresh token is stored as an HttpOnly Secure SameSite=Strict cookie
// set by the backend; it is invisible to JavaScript entirely.

let accessToken = null

export const getAccessToken = () => accessToken
export const setAccessToken = (token) => { accessToken = token }
export const clearAccessToken = () => { accessToken = null }
