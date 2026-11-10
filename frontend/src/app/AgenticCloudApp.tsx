import React, { useState, useEffect, useCallback } from 'react'
import { ChevronLeft } from 'lucide-react'
import {
	FileBrowser,
	FileBrowserProvider,
	defaultFileBrowserEditors,
	defaultFileBrowserPreviewers
} from '../index'
import { codeEditor, codePreviewer, jsonEditor, markupEditor } from '../plugins/code'
import { csvEditor, csvPreviewer } from '../plugins/csv'
import { docxPreviewer } from '../plugins/docx'
import { markdownEditor, markdownPreviewer } from '../plugins/markdown'
import { xlsxEditor, xlsxPreviewer } from '../plugins/xlsx'
import { AuthProvider, useAuth } from './context/AuthContext'
import { AuthModal } from './components/AuthModal'
import { Header } from './components/Header'
import { Sidebar, type SidebarTab } from './components/Sidebar'
import { ShareModal } from './components/ShareModal'
import { SharedWithMeView } from './components/SharedWithMeView'
import { PublicShareView } from './components/PublicShareView'

const PREVIEWERS = [
	codePreviewer,
	markdownPreviewer,
	csvPreviewer,
	xlsxPreviewer,
	docxPreviewer,
	...defaultFileBrowserPreviewers
]

const EDITORS = [
	codeEditor,
	jsonEditor,
	markupEditor,
	markdownEditor,
	csvEditor,
	xlsxEditor,
	...defaultFileBrowserEditors
]

const AgenticCloudInner: React.FC<{ onSwitchDemo?: () => void }> = ({ onSwitchDemo }) => {
	const { isAuthenticated, isLoading, adapter, user } = useAuth()
	const [currentTab, setCurrentTab] = useState<SidebarTab>('files')
	const [shareTarget, setShareTarget] = useState<{ id: string | number; name: string; isFolder: boolean } | null>(null)

	// URL query parameter (?path=...) navigation state
	const [currentPath, setCurrentPath] = useState<string>(() => {
		if (typeof window !== 'undefined') {
			const p = new URLSearchParams(window.location.search)
			const pathVal = p.get('path')
			if (pathVal) return pathVal
		}
		return '/'
	})

	const handlePathChange = useCallback((newPath: string) => {
		setCurrentPath(newPath)
		if (typeof window !== 'undefined') {
			const url = new URL(window.location.href)
			if (newPath && newPath !== '/') {
				url.searchParams.set('path', newPath)
			} else {
				url.searchParams.delete('path')
			}
			window.history.pushState({ path: newPath }, '', url.toString())
		}
	}, [])

	// Listen to browser Back and Forward button events (popstate)
	useEffect(() => {
		const handlePopState = () => {
			if (typeof window !== 'undefined') {
				const params = new URLSearchParams(window.location.search)
				const pathInUrl = params.get('path') || '/'
				setCurrentPath(pathInUrl)
			}
		}
		window.addEventListener('popstate', handlePopState)
		return () => window.removeEventListener('popstate', handlePopState)
	}, [])

	const handleGoBack = useCallback(() => {
		if (typeof window !== 'undefined' && window.history.length > 1) {
			window.history.back()
		} else {
			const parts = currentPath.split('/').filter(Boolean)
			parts.pop()
			const parent = parts.length > 0 ? '/' + parts.join('/') : '/'
			handlePathChange(parent)
		}
	}, [currentPath, handlePathChange])

	// Check if URL has ?share=token
	const [publicShareToken, setPublicShareToken] = useState<string | null>(() => {
		if (typeof window !== 'undefined') {
			const p = new URLSearchParams(window.location.search)
			return p.get('share')
		}
		return null
	})

	// If accessing a public share link, show the public view directly
	if (publicShareToken) {
		return (
			<PublicShareView
				shareToken={publicShareToken}
				onBackHome={() => {
					window.history.replaceState({}, '', window.location.pathname)
					setPublicShareToken(null)
				}}
			/>
		)
	}

	if (isLoading) {
		return (
			<div className="flex h-screen w-full items-center justify-center bg-gray-50 dark:bg-neutral-950">
				<div className="flex flex-col items-center gap-3 text-gray-500">
					<div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
					<p className="text-sm font-medium">Đang khởi tạo Agentic Cloud...</p>
				</div>
			</div>
		)
	}

	if (!isAuthenticated || !adapter) {
		return (
			<div className="flex min-h-screen w-full items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-sky-50 p-4 dark:from-neutral-950 dark:via-neutral-900 dark:to-neutral-950">
				<AuthModal onSkipDemo={onSwitchDemo} />
			</div>
		)
	}

	return (
		<div className="flex h-screen w-full flex-col overflow-hidden bg-white dark:bg-neutral-950">
			<Header onOpenDemoSettings={onSwitchDemo} />

			<div className="flex flex-1 min-h-0 overflow-hidden">
				<Sidebar currentTab={currentTab} onSelectTab={setCurrentTab} />

				<main className="relative flex flex-1 flex-col min-h-0 min-w-0 overflow-hidden bg-white p-4 dark:bg-neutral-900">
					{currentTab === 'shared' ? (
						<SharedWithMeView />
					) : (
						<FileBrowserProvider>
							<div className="flex h-full w-full flex-col min-h-0">
								{/* Navigation Bar / Nút quay lại khi đang ở thư mục con */}
								{currentPath !== '/' && (
									<div className="mb-2 flex items-center justify-between gap-2 rounded-xl border border-gray-100 bg-gray-50/80 px-3 py-1.5 dark:border-neutral-800 dark:bg-neutral-800/40">
										<button
											type="button"
											onClick={handleGoBack}
											className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-2.5 py-1 text-xs font-semibold text-gray-700 shadow-sm transition hover:bg-gray-100 hover:text-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-200 dark:hover:bg-neutral-700 dark:hover:text-indigo-400"
											title="Quay lại thư mục trước đó"
										>
											<ChevronLeft size={16} />
											<span>Quay lại</span>
										</button>
										<span className="truncate text-xs font-medium text-gray-500 dark:text-neutral-400">
											Đang xem: <code className="text-gray-800 dark:text-neutral-200">{currentPath}</code>
										</span>
									</div>
								)}

								<FileBrowser
									adapter={adapter}
									path={currentPath}
									onPathChange={handlePathChange}
									className="flex-1 min-h-0 h-full w-full"
									rootLabel={user?.username ? `${user.username}'s Drive` : 'Tệp của tôi'}
									previewers={PREVIEWERS}
									editors={EDITORS}
									initialView="list"
									density="comfortable"
									renderItemMeta={(item) => (
										<button
											type="button"
											onClick={(e) => {
												e.stopPropagation()
												if (item.id) {
													setShareTarget({
														id: item.id,
														name: item.name,
														isFolder: item.kind === 'folder'
													})
												}
											}}
											className="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 hover:bg-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-300 dark:hover:bg-indigo-900"
											title="Chia sẻ tệp hoặc thư mục này"
										>
											<span>🔗</span>
											<span>Chia sẻ</span>
										</button>
									)}
								/>
							</div>
						</FileBrowserProvider>
					)}
				</main>
			</div>

			{/* Share Modal Dialog */}
			{shareTarget && (
				<ShareModal
					nodeId={shareTarget.id}
					nodeName={shareTarget.name}
					isFolder={shareTarget.isFolder}
					onClose={() => setShareTarget(null)}
				/>
			)}
		</div>
	)
}

export const AgenticCloudApp: React.FC<{ onSwitchDemo?: () => void; gatewayUrl?: string }> = ({
	onSwitchDemo,
	gatewayUrl
}) => {
	return (
		<AuthProvider gatewayUrl={gatewayUrl}>
			<AgenticCloudInner onSwitchDemo={onSwitchDemo} />
		</AuthProvider>
	)
}
