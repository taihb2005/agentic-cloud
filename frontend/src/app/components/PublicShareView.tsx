import React, { useEffect, useState, useCallback } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { apiClient } from '../../adapters/agentic-cloud/api-client'
import type { NodeResponse, PublicShareInfoResponse } from '../../adapters/agentic-cloud/types'

function formatBytes(bytes?: number | null): string {
	if (!bytes || bytes <= 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(1024))
	return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export const PublicShareView: React.FC<{
	shareToken: string
	onBackHome: () => void
}> = ({ shareToken, onBackHome }) => {
	const [info, setInfo] = useState<PublicShareInfoResponse | null>(null)
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState<string | null>(null)

	// Password session state
	const [isPasswordRequired, setIsPasswordRequired] = useState(false)
	const [password, setPassword] = useState('')
	const [showPassword, setShowPassword] = useState(false)
	const [sessionToken, setSessionToken] = useState<string | null>(null)
	const [unlocking, setUnlocking] = useState(false)
	const [unlockError, setUnlockError] = useState<string | null>(null)

	// Folder browsing state (if shared item is a folder)
	const [folderNodes, setFolderNodes] = useState<NodeResponse[]>([])
	const [loadingNodes, setLoadingNodes] = useState(false)
	const [currentFolderId, setCurrentFolderId] = useState<string | undefined>(undefined)

	// Load public share info
	const loadShareInfo = useCallback(async () => {
		setLoading(true)
		setError(null)
		try {
			const res = await apiClient.getPublicShareInfo(shareToken)
			if (res.success && res.data) {
				setInfo(res.data)
				if (res.data.is_password_protected && !sessionToken) {
					setIsPasswordRequired(true)
				}
			} else {
				throw new Error(res.message || 'Không tìm thấy liên kết chia sẻ hoặc đã hết hạn')
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Liên kết không tồn tại hoặc đã hết hạn')
		} finally {
			setLoading(false)
		}
	}, [shareToken, sessionToken])

	useEffect(() => {
		loadShareInfo()
	}, [loadShareInfo])

	// Unlock with password
	const handleUnlock = async (e: React.FormEvent) => {
		e.preventDefault()
		setUnlocking(true)
		setUnlockError(null)
		try {
			const res = await apiClient.accessPublicShare(shareToken, password)
			if (res.success && res.data?.access_token) {
				setSessionToken(res.data.access_token)
				setIsPasswordRequired(false)
			} else {
				throw new Error(res.message || 'Mật khẩu không chính xác')
			}
		} catch (err: unknown) {
			setUnlockError(err instanceof Error ? err.message : 'Mở khóa thất bại')
		} finally {
			setUnlocking(false)
		}
	}

	// Load child nodes if it's a folder
	const loadNodes = useCallback(async () => {
		if (!info?.is_folder || isPasswordRequired) return
		setLoadingNodes(true)
		try {
			const res = await apiClient.getPublicShareNodes(shareToken, currentFolderId, sessionToken || undefined)
			if (res.success && res.data) {
				setFolderNodes(res.data.content || [])
			}
		} catch (err: unknown) {
			console.error('Lỗi tải danh sách tệp con:', err)
		} finally {
			setLoadingNodes(false)
		}
	}, [info?.is_folder, isPasswordRequired, shareToken, currentFolderId, sessionToken])

	useEffect(() => {
		loadNodes()
	}, [loadNodes])

	// Download single file
	const handleDownload = async (nodeId?: number | string) => {
		try {
			const targetId = nodeId ?? info?.node_id
			if (!targetId) return
			const res = await apiClient.getPublicDownloadUrl(shareToken, targetId, sessionToken || undefined)
			if (res.success && res.data?.url) {
				const a = document.createElement('a')
				a.href = res.data.url
				a.download = res.data.file_name || 'download'
				a.target = '_blank'
				document.body.appendChild(a)
				a.click()
				a.remove()
			}
		} catch (err: unknown) {
			alert(err instanceof Error ? err.message : 'Tải xuống thất bại')
		}
	}

	return (
		<div className="flex min-h-screen w-full flex-col bg-gray-50 dark:bg-neutral-950">
			{/* Navbar */}
			<header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6 dark:border-neutral-800 dark:bg-neutral-900">
				<div className="flex items-center gap-3">
					<div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 font-bold text-white shadow-md shadow-indigo-600/30">
						☁️
					</div>
					<div>
						<h1 className="text-base font-bold text-gray-900 dark:text-white">Agentic Cloud Storage</h1>
						<p className="text-xs text-gray-400">Liên kết chia sẻ công khai</p>
					</div>
				</div>

				<button
					type="button"
					onClick={onBackHome}
					className="rounded-xl border border-gray-200 px-3.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-100 dark:border-neutral-700 dark:text-neutral-300 dark:hover:bg-neutral-800"
				>
					Về trang chủ ➔
				</button>
			</header>

			{/* Main Content */}
			<main className="flex flex-1 items-center justify-center p-6">
				{loading ? (
					<div className="text-center text-xs text-gray-400">
						<div className="mx-auto mb-3 h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
						Đang kiểm tra liên kết chia sẻ...
					</div>
				) : error ? (
					<div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 text-center shadow-lg dark:border-neutral-800 dark:bg-neutral-900">
						<span className="text-4xl">⚠️</span>
						<h2 className="mt-3 text-lg font-bold text-gray-900 dark:text-white">Không thể truy cập</h2>
						<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">{error}</p>
						<button
							type="button"
							onClick={onBackHome}
							className="mt-6 rounded-xl bg-indigo-600 px-5 py-2 text-xs font-semibold text-white hover:bg-indigo-700"
						>
							Quay lại trang chủ
						</button>
					</div>
				) : isPasswordRequired ? (
					/* Password Prompt */
					<form
						onSubmit={handleUnlock}
						className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-xl dark:border-neutral-800 dark:bg-neutral-900"
					>
						<div className="text-center">
							<span className="text-4xl">🔒</span>
							<h2 className="mt-2 text-lg font-bold text-gray-900 dark:text-white">Tệp được bảo vệ</h2>
							<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">
								Vui lòng nhập mật khẩu do người chia sẻ cung cấp để xem nội dung.
							</p>
						</div>

						{unlockError && (
							<div className="mt-4 rounded-xl bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
								{unlockError}
							</div>
						)}

						<div className="relative mt-4">
							<input
								type={showPassword ? 'text' : 'password'}
								placeholder="Mật khẩu"
								required
								autoFocus
								value={password}
								onChange={(e) => setPassword(e.target.value)}
								className="w-full rounded-xl border border-gray-300 px-3.5 py-2.5 pr-10 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
							/>
							<button
								type="button"
								onClick={() => setShowPassword(!showPassword)}
								className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:text-neutral-400 dark:hover:text-neutral-200 transition"
								title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
								aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
							>
								{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
							</button>
						</div>

						<button
							type="submit"
							disabled={unlocking}
							className="mt-4 w-full rounded-xl bg-indigo-600 py-2.5 text-xs font-semibold text-white shadow-md shadow-indigo-600/20 hover:bg-indigo-700 disabled:opacity-50"
						>
							{unlocking ? 'Đang xác thực...' : 'Mở khóa truy cập'}
						</button>
					</form>
				) : info ? (
					/* Shared Item Content */
					<div className="w-full max-w-2xl rounded-2xl border border-gray-200 bg-white p-6 shadow-xl dark:border-neutral-800 dark:bg-neutral-900">
						{/* Item Header */}
						<div className="flex items-start justify-between gap-4 border-b border-gray-100 pb-5 dark:border-neutral-800">
							<div className="flex items-center gap-3">
								<span className="text-4xl">{info.is_folder ? '📁' : '📄'}</span>
								<div>
									<h2 className="text-lg font-bold text-gray-900 dark:text-white">{info.node_name}</h2>
									<div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-neutral-400">
										<span>{info.is_folder ? 'Thư mục' : formatBytes(info.size_bytes)}</span>
										<span>• Chia sẻ bởi: <strong className="text-gray-700 dark:text-neutral-200">{info.owner_name || 'Người dùng'}</strong></span>
										<span className="rounded-md bg-indigo-50 px-2 py-0.5 text-[10px] font-semibold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
											{info.permission === 'EDIT' ? 'Cho phép chỉnh sửa' : 'Chỉ xem'}
										</span>
									</div>
								</div>
							</div>

							{!info.is_folder && (
								<button
									type="button"
									onClick={() => handleDownload()}
									className="rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-md shadow-indigo-600/20 hover:bg-indigo-700"
								>
									Tải xuống ⬇
								</button>
							)}
						</div>

						{/* Folder children list */}
						{info.is_folder && (
							<div className="mt-5">
								<div className="mb-2 text-xs font-semibold text-gray-700 dark:text-neutral-300">
									Danh sách tệp và thư mục con ({folderNodes.length})
								</div>

								{loadingNodes ? (
									<div className="py-8 text-center text-xs text-gray-400">Đang tải danh sách tệp...</div>
								) : folderNodes.length === 0 ? (
									<div className="rounded-xl border border-dashed border-gray-200 p-8 text-center text-xs text-gray-400 dark:border-neutral-800">
										Thư mục này hiện đang trống.
									</div>
								) : (
									<div className="divide-y divide-gray-100 rounded-xl border border-gray-200 dark:divide-neutral-800 dark:border-neutral-800">
										{folderNodes.map((child) => (
											<div
												key={child.id}
												className="flex items-center justify-between p-3 text-xs transition hover:bg-gray-50 dark:hover:bg-neutral-800/60"
											>
												<div className="flex items-center gap-2.5">
													<span>{child.is_folder ? '📁' : '📄'}</span>
													<div>
														<div className="font-semibold text-gray-900 dark:text-white">{child.name}</div>
														<div className="text-[11px] text-gray-400">
															{child.is_folder ? 'Thư mục' : formatBytes(child.size_bytes)}
														</div>
													</div>
												</div>

												{child.is_folder ? (
													<button
														type="button"
														onClick={() => setCurrentFolderId(String(child.id))}
														className="text-xs font-semibold text-indigo-600 hover:text-indigo-700"
													>
														Mở ➔
													</button>
												) : (
													<button
														type="button"
														onClick={() => handleDownload(child.id)}
														className="rounded-lg bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-700 hover:bg-indigo-50 hover:text-indigo-600 dark:bg-neutral-800 dark:text-neutral-300"
													>
														Tải về ⬇
													</button>
												)}
											</div>
										))}
									</div>
								)}
							</div>
						)}
					</div>
				) : null}
			</main>
		</div>
	)
}
