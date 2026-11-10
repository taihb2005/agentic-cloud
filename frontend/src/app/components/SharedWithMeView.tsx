import React, { useEffect, useState, useCallback } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { apiClient } from '../../adapters/agentic-cloud/api-client'
import type { SharedWithMeNodeResponse } from '../../adapters/agentic-cloud/types'

function formatBytes(bytes?: number | null): string {
	if (!bytes || bytes <= 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(1024))
	return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export const SharedWithMeView: React.FC<{
	onOpenNode?: (nodeId: number | string, nodeName: string, isFolder: boolean) => void
}> = ({ onOpenNode }) => {
	const [items, setItems] = useState<SharedWithMeNodeResponse[]>([])
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState<string | null>(null)
	const [searchName, setSearchName] = useState('')
	const [unlockNodeId, setUnlockNodeId] = useState<number | string | null>(null)
	const [unlockPassword, setUnlockPassword] = useState('')
	const [showUnlockPassword, setShowUnlockPassword] = useState(false)
	const [unlocking, setUnlocking] = useState(false)
	const [unlockError, setUnlockError] = useState<string | null>(null)

	const loadSharedNodes = useCallback(async () => {
		setLoading(true)
		setError(null)
		try {
			const res = await apiClient.getSharedWithMe({
				name: searchName.trim() || undefined
			})
			if (res.success && res.data) {
				setItems(res.data.content || [])
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Không thể tải danh sách chia sẻ')
		} finally {
			setLoading(false)
		}
	}, [searchName])

	useEffect(() => {
		loadSharedNodes()
	}, [loadSharedNodes])

	const handleUnlock = async (e: React.FormEvent) => {
		e.preventDefault()
		if (!unlockNodeId) return
		setUnlocking(true)
		setUnlockError(null)
		try {
			const res = await apiClient.unlockNode(unlockNodeId, unlockPassword)
			if (res.success) {
				setUnlockNodeId(null)
				setUnlockPassword('')
				loadSharedNodes()
			} else {
				throw new Error(res.message || 'Mật khẩu không chính xác')
			}
		} catch (err: unknown) {
			setUnlockError(err instanceof Error ? err.message : 'Mở khóa thất bại')
		} finally {
			setUnlocking(false)
		}
	}

	const handleDownloadFile = async (node: SharedWithMeNodeResponse) => {
		try {
			const res = await apiClient.post<import('../../adapters/agentic-cloud/types').DownloadUrlsResponse>(
				'/api/v1/storage-service/files/download-urls',
				{ files: [{ node_id: String(node.node_id) }] },
				{
					'Content-Disposition': 'attachment',
					'X-Download': 'true'
				}
			)
			if (res.success && res.data?.download_urls?.length) {
				const downloadUrl = res.data.download_urls[0].url
				const a = document.createElement('a')
				a.href = downloadUrl
				a.download = node.node_name
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
		<div className="flex h-full w-full flex-col overflow-hidden bg-white p-6 dark:bg-neutral-900">
			{/* Top bar */}
			<div className="mb-6 flex flex-wrap items-center justify-between gap-4">
				<div>
					<h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
						Được chia sẻ với tôi
					</h2>
					<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">
						Danh sách các tệp và thư mục người khác đã cấp quyền cho bạn
					</p>
				</div>

				<div className="flex items-center gap-2">
					<input
						type="text"
						placeholder="Tìm kiếm tệp được chia sẻ..."
						value={searchName}
						onChange={(e) => setSearchName(e.target.value)}
						className="rounded-xl border border-gray-300 bg-white px-3.5 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
					/>
					<button
						type="button"
						onClick={loadSharedNodes}
						className="rounded-xl border border-gray-200 bg-white px-3.5 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-300"
					>
						🔄 Làm mới
					</button>
				</div>
			</div>

			{error && (
				<div className="mb-4 rounded-xl bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
					{error}
				</div>
			)}

			{/* List */}
			<div className="flex-1 min-h-0 overflow-y-auto">
				{loading ? (
					<div className="flex h-48 items-center justify-center text-xs text-gray-400">
						Đang tải danh sách được chia sẻ...
					</div>
				) : items.length === 0 ? (
					<div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 text-center dark:border-neutral-800">
						<span className="text-4xl">📂</span>
						<p className="mt-2 text-sm font-semibold text-gray-700 dark:text-neutral-300">
							Chưa có tệp nào được chia sẻ với bạn
						</p>
						<p className="text-xs text-gray-400">
							Khi người dùng khác chia sẻ tài liệu cho bạn, chúng sẽ xuất hiện ở đây.
						</p>
					</div>
				) : (
					<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
						{items.map((item) => (
							<div
								key={item.node_id}
								className="flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-4 shadow-sm transition hover:shadow-md dark:border-neutral-800 dark:bg-neutral-800/60"
							>
								<div>
									<div className="flex items-start justify-between gap-2">
										<div className="flex items-center gap-2.5">
											<span className="text-2xl">{item.is_folder ? '📁' : '📄'}</span>
											<div>
												<h4 className="text-sm font-semibold text-gray-900 line-clamp-1 dark:text-white" title={item.name}>
													{item.name}
												</h4>
												<div className="text-[11px] text-gray-400">
													{item.is_folder ? 'Thư mục' : formatBytes(item.size_bytes)}
												</div>
											</div>
										</div>
										<span
											className={`rounded-lg px-2 py-0.5 text-[10px] font-bold ${
												item.permission === 'EDIT'
													? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
													: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
											}`}
										>
											{item.permission === 'EDIT' ? 'Chỉnh sửa' : 'Chỉ xem'}
										</span>
									</div>

									<div className="mt-4 border-t border-gray-100 pt-3 text-[11px] text-gray-500 dark:border-neutral-700 dark:text-neutral-400 space-y-1">
										<div>
											Người chia sẻ:{' '}
											<strong className="text-gray-800 dark:text-neutral-200">
												{item.owner?.username || 'Chủ sở hữu'}
											</strong>
										</div>
										{item.shared_at && (
											<div>
												Ngày chia sẻ: {new Date(item.shared_at).toLocaleDateString('vi-VN')}
											</div>
										)}
									</div>
								</div>

								<div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-neutral-700">
									{item.is_password_protected && !item.is_unlocked ? (
										<button
											type="button"
											onClick={() => setUnlockNodeId(item.node_id)}
											className="rounded-lg bg-amber-500 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-amber-600"
										>
											🔒 Mở khóa mật khẩu
										</button>
									) : (
										<>
											{onOpenNode && item.is_folder && (
												<button
													type="button"
													onClick={() => onOpenNode(item.node_id, item.name, true)}
													className="rounded-lg bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-600 hover:bg-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-300"
												>
													Mở thư mục ➔
												</button>
											)}
											{!item.is_folder && (
												<button
													type="button"
													onClick={() => handleDownloadFile(item)}
													className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700"
												>
													Tải xuống ⬇
												</button>
											)}
										</>
									)}
								</div>
							</div>
						))}
					</div>
				)}
			</div>

			{/* Password Unlock Modal */}
			{unlockNodeId && (
				<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
					<form
						onSubmit={handleUnlock}
						className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl dark:bg-neutral-900"
					>
						<h3 className="text-base font-bold text-gray-900 dark:text-white">
							Nhập mật khẩu mở khóa
						</h3>
						<p className="mt-1 text-xs text-gray-500 dark:text-neutral-400">
							Mục này đã được chủ sở hữu bảo vệ bằng mật khẩu bảo mật.
						</p>

						{unlockError && (
							<div className="mt-3 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
								{unlockError}
							</div>
						)}

						<div className="relative mt-4">
							<input
								type={showUnlockPassword ? 'text' : 'password'}
								placeholder="Mật khẩu"
								required
								autoFocus
								value={unlockPassword}
								onChange={(e) => setUnlockPassword(e.target.value)}
								className="w-full rounded-xl border border-gray-300 px-3.5 py-2.5 pr-10 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
							/>
							<button
								type="button"
								onClick={() => setShowUnlockPassword(!showUnlockPassword)}
								className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:text-neutral-400 dark:hover:text-neutral-200 transition"
								title={showUnlockPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
								aria-label={showUnlockPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
							>
								{showUnlockPassword ? <EyeOff size={16} /> : <Eye size={16} />}
							</button>
						</div>

						<div className="mt-4 flex justify-end gap-2">
							<button
								type="button"
								onClick={() => setUnlockNodeId(null)}
								className="rounded-xl border border-gray-200 px-4 py-2 text-xs font-semibold text-gray-600 hover:bg-gray-50 dark:border-neutral-700 dark:text-neutral-300"
							>
								Hủy
							</button>
							<button
								type="submit"
								disabled={unlocking}
								className="rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
							>
								{unlocking ? 'Đang kiểm tra...' : 'Mở khóa'}
							</button>
						</div>
					</form>
				</div>
			)}
		</div>
	)
}
