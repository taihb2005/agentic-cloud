import React, { useEffect, useState, useCallback } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { apiClient } from '../../adapters/agentic-cloud/api-client'
import type {
	ConfigureShareRequest,
	NodeShareDetailResponse,
	SharedUserItemResponse,
	UserSearchResponse
} from '../../adapters/agentic-cloud/types'

interface ShareModalProps {
	nodeId: number | string
	nodeName: string
	isFolder: boolean
	onClose: () => void
}

export const ShareModal: React.FC<ShareModalProps> = ({ nodeId, nodeName, isFolder, onClose }) => {
	const [activeTab, setActiveTab] = useState<'USER_GROUP' | 'PUBLIC_LINK'>('PUBLIC_LINK')
	const [loading, setLoading] = useState(true)
	const [saving, setSaving] = useState(false)
	const [error, setError] = useState<string | null>(null)
	const [successMsg, setSuccessMsg] = useState<string | null>(null)
	const [shareDetail, setShareDetail] = useState<NodeShareDetailResponse | null>(null)

	// User Group state
	const [userSearchQuery, setUserSearchQuery] = useState('')
	const [searchResults, setSearchResults] = useState<UserSearchResponse[]>([])
	const [isSearching, setIsSearching] = useState(false)
	const [selectedUsers, setSelectedUsers] = useState<Array<{ id: string; username: string; email: string }>>([])
	const [userPermission, setUserPermission] = useState<'VIEW' | 'EDIT'>('VIEW')

	// Public link state
	const [publicPermission, setPublicPermission] = useState<'VIEW' | 'EDIT'>('VIEW')
	const [enablePassword, setEnablePassword] = useState(false)
	const [password, setPassword] = useState('')
	const [showPassword, setShowPassword] = useState(false)
	const [expiresAt, setExpiresAt] = useState('')
	const [maxAccessCount, setMaxAccessCount] = useState<number | undefined>(undefined)
	const [copied, setCopied] = useState(false)

	// Load existing share configuration
	const loadShareDetail = useCallback(async () => {
		setLoading(true)
		setError(null)
		try {
			const res = await apiClient.getShareDetail(nodeId)
			if (res.success && res.data) {
				const data = res.data
				setShareDetail(data)
				if (data.share_mode === 'USER_GROUP') {
					setActiveTab('USER_GROUP')
					if (data.permission === 'VIEW' || data.permission === 'EDIT') {
						setUserPermission(data.permission)
					}
					if (data.users && data.users.length > 0) {
						setSelectedUsers(
							data.users.map((u) => ({
								id: u.user_id,
								username: u.username,
								email: u.email || ''
							}))
						)
					}
				} else if (data.share_mode === 'PUBLIC_LINK') {
					setActiveTab('PUBLIC_LINK')
					if (data.permission === 'VIEW' || data.permission === 'EDIT') {
						setPublicPermission(data.permission)
					}
					if (data.is_password_protected) {
						setEnablePassword(true)
					}
					if (data.expires_at) {
						setExpiresAt(data.expires_at.slice(0, 16))
					}
					if (data.max_access_count) {
						setMaxAccessCount(data.max_access_count)
					}
				}
			}
		} catch (err: unknown) {
			// If not shared yet, it might return 404/not shared, which is normal
			console.log('Chưa có cấu hình chia sẻ trước đó:', err)
		} finally {
			setLoading(false)
		}
	}, [nodeId])

	useEffect(() => {
		loadShareDetail()
	}, [loadShareDetail])

	// Debounced user search
	useEffect(() => {
		if (activeTab !== 'USER_GROUP' || !userSearchQuery.trim()) {
			setSearchResults([])
			return
		}
		const timer = setTimeout(async () => {
			setIsSearching(true)
			try {
				const res = await apiClient.searchUsers(userSearchQuery.trim(), 8)
				if (res.success && res.data) {
					// Filter out already selected users
					const existingIds = new Set(selectedUsers.map((u) => u.id))
					setSearchResults(res.data.filter((u) => !existingIds.has(u.id)))
				}
			} catch (err: unknown) {
				console.error('Lỗi tìm kiếm user:', err)
			} finally {
				setIsSearching(false)
			}
		}, 300)
		return () => clearTimeout(timer)
	}, [userSearchQuery, activeTab, selectedUsers])

	const handleAddUser = (user: UserSearchResponse) => {
		setSelectedUsers((prev) => [...prev, { id: user.id, username: user.username, email: user.email }])
		setUserSearchQuery('')
		setSearchResults([])
	}

	const handleRemoveUser = async (userId: string) => {
		setSelectedUsers((prev) => prev.filter((u) => u.id !== userId))
		if (shareDetail?.share_mode === 'USER_GROUP') {
			try {
				await apiClient.revokeUserShare(nodeId, userId)
			} catch {
				// handled silently
			}
		}
	}

	// Save Public Link Share
	const handleSavePublicLink = async () => {
		setSaving(true)
		setError(null)
		setSuccessMsg(null)
		try {
			const req: ConfigureShareRequest = {
				share_mode: 'PUBLIC_LINK',
				permission: publicPermission,
				override_existing: true
			}
			if (enablePassword && password.trim()) {
				req.password = password.trim()
			}
			if (expiresAt) {
				req.expiresAt = new Date(expiresAt).toISOString()
			}
			if (maxAccessCount && maxAccessCount > 0) {
				req.max_access_count = maxAccessCount
			}

			const res = await apiClient.configureShare(nodeId, req)
			if (res.success && res.data) {
				setShareDetail(res.data)
				setSuccessMsg('Đã tạo liên kết chia sẻ công khai thành công!')
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Thiết lập liên kết thất bại')
		} finally {
			setSaving(false)
		}
	}

	// Save User Group Share
	const handleSaveUserGroup = async () => {
		if (selectedUsers.length === 0) {
			setError('Vui lòng chọn ít nhất một người dùng để chia sẻ')
			return
		}
		setSaving(true)
		setError(null)
		setSuccessMsg(null)
		try {
			const req: ConfigureShareRequest = {
				share_mode: 'USER_GROUP',
				permission: userPermission,
				user_ids: selectedUsers.map((u) => u.id),
				override_existing: true
			}
			const res = await apiClient.configureShare(nodeId, req)
			if (res.success && res.data) {
				setShareDetail(res.data)
				setSuccessMsg(`Đã chia sẻ thành công cho ${selectedUsers.length} người dùng!`)
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Chia sẻ thất bại')
		} finally {
			setSaving(false)
		}
	}

	// Revoke Share (revert to Private)
	const handleRevokeShare = async () => {
		if (!confirm('Bạn có chắc chắn muốn hủy chia sẻ mục này? Mọi người dùng hoặc liên kết sẽ không thể truy cập nữa.')) {
			return
		}
		setSaving(true)
		setError(null)
		try {
			await apiClient.revokeShare(nodeId)
			setShareDetail(null)
			setSelectedUsers([])
			setPassword('')
			setEnablePassword(false)
			setSuccessMsg('Đã hủy chia sẻ. Tệp hiện ở chế độ riêng tư.')
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : 'Hủy chia sẻ thất bại')
		} finally {
			setSaving(false)
		}
	}

	const getPublicUrl = () => {
		if (!shareDetail?.share_token) return ''
		return `${window.location.origin}?share=${shareDetail.share_token}`
	}

	const handleCopy = () => {
		const url = getPublicUrl()
		if (url) {
			navigator.clipboard.writeText(url)
			setCopied(true)
			setTimeout(() => setCopied(false), 2000)
		}
	}

	return (
		<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
			<div className="flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-neutral-900 dark:border dark:border-neutral-800">
				{/* Header */}
				<div className="flex items-center justify-between border-b border-gray-100 p-5 dark:border-neutral-800">
					<div className="flex items-center gap-3">
						<span className="text-2xl">{isFolder ? '📁' : '📄'}</span>
						<div>
							<h3 className="text-base font-bold text-gray-900 dark:text-white">
								Chia sẻ: {nodeName}
							</h3>
							<p className="text-xs text-gray-500 dark:text-neutral-400">
								{isFolder ? 'Thư mục' : 'Tệp tin'} • ID: {nodeId}
							</p>
						</div>
					</div>
					<button
						type="button"
						onClick={onClose}
						className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-neutral-800 dark:hover:text-white"
					>
						✕
					</button>
				</div>

				{/* Tabs */}
				<div className="flex border-b border-gray-100 bg-gray-50/60 p-2 dark:border-neutral-800 dark:bg-neutral-900/60">
					<button
						type="button"
						onClick={() => setActiveTab('PUBLIC_LINK')}
						className={`flex-1 rounded-xl py-2 text-xs font-semibold transition ${
							activeTab === 'PUBLIC_LINK'
								? 'bg-white text-indigo-600 shadow-sm dark:bg-neutral-800 dark:text-white'
								: 'text-gray-500 hover:text-gray-900 dark:text-neutral-400 dark:hover:text-white'
						}`}
					>
						🔗 Liên kết công khai
					</button>
					<button
						type="button"
						onClick={() => setActiveTab('USER_GROUP')}
						className={`flex-1 rounded-xl py-2 text-xs font-semibold transition ${
							activeTab === 'USER_GROUP'
								? 'bg-white text-indigo-600 shadow-sm dark:bg-neutral-800 dark:text-white'
								: 'text-gray-500 hover:text-gray-900 dark:text-neutral-400 dark:hover:text-white'
						}`}
					>
						👥 Gán người dùng
					</button>
				</div>

				{/* Body Content */}
				<div className="flex-1 overflow-y-auto p-5 space-y-4">
					{error && (
						<div className="rounded-xl bg-rose-50 p-3 text-xs text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
							{error}
						</div>
					)}
					{successMsg && (
						<div className="rounded-xl bg-emerald-50 p-3 text-xs text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
							{successMsg}
						</div>
					)}

					{loading ? (
						<div className="py-8 text-center text-xs text-gray-400">Đang tải cấu hình chia sẻ...</div>
					) : activeTab === 'PUBLIC_LINK' ? (
						/* ================= TAB 1: PUBLIC LINK ================= */
						<div className="space-y-4">
							{shareDetail?.share_mode === 'PUBLIC_LINK' && shareDetail.share_token ? (
								<div className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-4 dark:border-indigo-950 dark:bg-indigo-950/20">
									<label className="block text-xs font-semibold text-indigo-900 dark:text-indigo-300">
										Đường dẫn chia sẻ công khai
									</label>
									<div className="mt-1.5 flex items-center gap-2">
										<input
											type="text"
											readOnly
											value={getPublicUrl()}
											className="flex-1 rounded-lg border border-indigo-200 bg-white px-3 py-2 text-xs font-mono text-gray-700 outline-none dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-200"
										/>
										<button
											type="button"
											onClick={handleCopy}
											className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white transition hover:bg-indigo-700"
										>
											{copied ? '✓ Đã chép' : 'Sao chép'}
										</button>
									</div>
									<div className="mt-2 flex flex-wrap gap-2 text-[11px] text-gray-500 dark:text-neutral-400">
										<span>Quyền: <strong>{shareDetail.permission === 'EDIT' ? 'Chỉnh sửa' : 'Chỉ xem'}</strong></span>
										{shareDetail.is_password_protected && <span>• 🔒 Có mật khẩu</span>}
										{shareDetail.expires_at && (
											<span>• Hết hạn: {new Date(shareDetail.expires_at).toLocaleString('vi-VN')}</span>
										)}
									</div>
								</div>
							) : (
								<p className="text-xs text-gray-500 dark:text-neutral-400">
									Bất kỳ ai có liên kết đều có thể truy cập mục này theo các quyền được thiết lập bên dưới.
								</p>
							)}

							<div>
								<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
									Quyền hạn của người có liên kết
								</label>
								<select
									value={publicPermission}
									onChange={(e) => setPublicPermission(e.target.value as 'VIEW' | 'EDIT')}
									className="mt-1.5 w-full rounded-xl border border-gray-300 bg-white px-3 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
								>
									<option value="VIEW">👀 Người xem (Xem và tải xuống)</option>
									<option value="EDIT">✏️ Người chỉnh sửa (Xem, tải xuống, đổi tên, tải lên)</option>
								</select>
							</div>

							{/* Password protection */}
							<div className="rounded-xl border border-gray-200 p-3.5 dark:border-neutral-800">
								<label className="flex items-center gap-2 text-xs font-semibold text-gray-800 dark:text-neutral-200 cursor-pointer">
									<input
										type="checkbox"
										checked={enablePassword}
										onChange={(e) => setEnablePassword(e.target.checked)}
										className="rounded text-indigo-600"
									/>
									<span>Bảo vệ bằng mật khẩu</span>
								</label>
								{enablePassword && (
									<div className="relative mt-2">
										<input
											type={showPassword ? 'text' : 'password'}
											placeholder="Mật khẩu"
											value={password}
											onChange={(e) => setPassword(e.target.value)}
											className="w-full rounded-lg border border-gray-300 px-3 py-1.5 pr-8 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
										/>
										<button
											type="button"
											onClick={() => setShowPassword(!showPassword)}
											className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:text-neutral-400 dark:hover:text-neutral-200 transition"
											title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
											aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
										>
											{showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
										</button>
									</div>
								)}
							</div>

							{/* Expiration date */}
							<div className="grid grid-cols-2 gap-3">
								<div>
									<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
										Thời hạn liên kết
									</label>
									<input
										type="datetime-local"
										value={expiresAt}
										onChange={(e) => setExpiresAt(e.target.value)}
										className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
									/>
								</div>
								<div>
									<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
										Giới hạn số lượt xem/tải
									</label>
									<input
										type="number"
										min={1}
										placeholder="Không giới hạn"
										value={maxAccessCount ?? ''}
										onChange={(e) => setMaxAccessCount(e.target.value ? Number(e.target.value) : undefined)}
										className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
									/>
								</div>
							</div>

							<button
								type="button"
								disabled={saving}
								onClick={handleSavePublicLink}
								className="w-full rounded-xl bg-indigo-600 py-2.5 text-xs font-semibold text-white shadow-md shadow-indigo-600/20 hover:bg-indigo-700 disabled:opacity-50"
							>
								{saving ? 'Đang lưu...' : shareDetail?.share_mode === 'PUBLIC_LINK' ? 'Cập nhật liên kết' : 'Tạo liên kết chia sẻ'}
							</button>
						</div>
					) : (
						/* ================= TAB 2: USER GROUP ================= */
						<div className="space-y-4">
							<div>
								<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
									Tìm kiếm người dùng theo tên hoặc email
								</label>
								<div className="relative mt-1.5">
									<input
										type="text"
										value={userSearchQuery}
										onChange={(e) => setUserSearchQuery(e.target.value)}
										placeholder="Gõ tên hoặc email người dùng..."
										className="w-full rounded-xl border border-gray-300 bg-white px-3.5 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
									/>
									{isSearching && (
										<span className="absolute right-3 top-2.5 text-xs text-gray-400 animate-spin">⌛</span>
									)}
								</div>

								{/* Search Results Dropdown */}
								{searchResults.length > 0 && (
									<div className="mt-1.5 max-h-40 overflow-y-auto rounded-xl border border-gray-200 bg-white shadow-lg dark:border-neutral-700 dark:bg-neutral-800">
										{searchResults.map((u) => (
											<button
												key={u.id}
												type="button"
												onClick={() => handleAddUser(u)}
												className="flex w-full items-center justify-between px-3 py-2 text-left text-xs hover:bg-indigo-50 dark:hover:bg-neutral-700"
											>
												<div>
													<div className="font-semibold text-gray-900 dark:text-white">{u.username}</div>
													<div className="text-[11px] text-gray-500 dark:text-neutral-400">{u.email}</div>
												</div>
												<span className="text-xs font-semibold text-indigo-600 dark:text-indigo-400">+ Thêm</span>
											</button>
										))}
									</div>
								)}
							</div>

							<div>
								<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
									Quyền hạn nhóm người dùng
								</label>
								<select
									value={userPermission}
									onChange={(e) => setUserPermission(e.target.value as 'VIEW' | 'EDIT')}
									className="mt-1.5 w-full rounded-xl border border-gray-300 bg-white px-3 py-2 text-xs outline-none focus:border-indigo-600 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
								>
									<option value="VIEW">👀 Người xem (Chỉ được xem và tải)</option>
									<option value="EDIT">✏️ Người chỉnh sửa (Xem, tải, chỉnh sửa, thêm tệp)</option>
								</select>
							</div>

							{/* Selected users list */}
							<div>
								<label className="block text-xs font-semibold text-gray-700 dark:text-neutral-300">
									Danh sách người dùng được gán quyền ({selectedUsers.length})
								</label>
								{selectedUsers.length === 0 ? (
									<div className="mt-1 rounded-xl border border-dashed border-gray-200 p-4 text-center text-xs text-gray-400 dark:border-neutral-800">
										Chưa có người dùng nào được chọn.
									</div>
								) : (
									<div className="mt-1.5 max-h-36 overflow-y-auto space-y-1.5">
										{selectedUsers.map((u) => (
											<div
												key={u.id}
												className="flex items-center justify-between rounded-xl bg-gray-50 px-3 py-2 text-xs dark:bg-neutral-800"
											>
												<div className="flex items-center gap-2">
													<div className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-[10px] font-bold text-indigo-600 dark:bg-indigo-950 dark:text-indigo-300">
														{u.username.charAt(0).toUpperCase()}
													</div>
													<div>
														<span className="font-semibold text-gray-800 dark:text-neutral-200">{u.username}</span>
														{u.email && <span className="ml-1 text-[11px] text-gray-400">({u.email})</span>}
													</div>
												</div>
												<button
													type="button"
													onClick={() => handleRemoveUser(u.id)}
													className="text-xs text-rose-500 hover:text-rose-700"
												>
													Xóa
												</button>
											</div>
										))}
									</div>
								)}
							</div>

							<button
								type="button"
								disabled={saving || selectedUsers.length === 0}
								onClick={handleSaveUserGroup}
								className="w-full rounded-xl bg-indigo-600 py-2.5 text-xs font-semibold text-white shadow-md shadow-indigo-600/20 hover:bg-indigo-700 disabled:opacity-50"
							>
								{saving ? 'Đang lưu...' : 'Lưu quyền chia sẻ người dùng'}
							</button>
						</div>
					)}
				</div>

				{/* Footer */}
				<div className="flex items-center justify-between border-t border-gray-100 bg-gray-50/50 p-4 dark:border-neutral-800 dark:bg-neutral-900/50">
					{shareDetail?.share_mode && shareDetail.share_mode !== 'PRIVATE' ? (
						<button
							type="button"
							onClick={handleRevokeShare}
							disabled={saving}
							className="text-xs font-semibold text-rose-600 hover:text-rose-700 dark:text-rose-400"
						>
							🔒 Hủy chia sẻ (Về riêng tư)
						</button>
					) : (
						<span className="text-[11px] text-gray-400">Mục đang ở chế độ Riêng tư</span>
					)}
					<button
						type="button"
						onClick={onClose}
						className="rounded-xl border border-gray-200 bg-white px-4 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-300"
					>
						Đóng
					</button>
				</div>
			</div>
		</div>
	)
}
