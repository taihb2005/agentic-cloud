import { join } from 'node:path'

const PORT = Number(process.env.PORT) || 3000
const DIST_DIR = join(import.meta.dir, 'demo/dist')

const server = Bun.serve({
	port: PORT,
	async fetch(req) {
		const url = new URL(req.url)
		let pathname = url.pathname

		if (pathname === '/') {
			pathname = '/index.html'
		}

		const filePath = join(DIST_DIR, pathname)
		const file = Bun.file(filePath)

		if (await file.exists()) {
			return new Response(file)
		}

		// Fallback to index.html for SPA client-side routing
		const indexFile = Bun.file(join(DIST_DIR, 'index.html'))
		if (await indexFile.exists()) {
			return new Response(indexFile)
		}

		return new Response('404 Not Found', { status: 404 })
	}
})

console.log(`🚀 Agentic Cloud Frontend đang chạy tại: http://localhost:${server.port}`)
