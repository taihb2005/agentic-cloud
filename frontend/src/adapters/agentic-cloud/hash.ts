/**
 * Calculate SHA-256 hash of a file using standard Web Crypto API.
 */
export async function calculateSha256(file: File | Blob): Promise<string> {
	const buffer = await file.arrayBuffer()
	const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
	const hashArray = Array.from(new Uint8Array(hashBuffer))
	return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}
