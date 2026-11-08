import React from 'react'
import { useAuth } from '../context/AuthContext'

function formatBytes(bytes?: number | null): string {
	if (!bytes || bytes <= 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.floor(Math.log(bytes) / Math.log(1024))
	return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export const Sidebar: React.FC<{
	currentTab: 'files' | 'starred' | 'trash'
	onSelectTab: (tab: 'files' | 'starred' | 'trash') => void
}> = ({ currentTab, onSelectTab }) => {
	const { user, quota, refreshQuota } = useAuth()

	const currentQuotaBytes = quota?.current_quota ?? user?.storage?.storage_current_quota_bytes ?? 0
	const maxQuotaBytes = quota?.max_quota ?? user?.storage?.storage_max_quota_bytes ?? 16106127360 // default 15GB
	const percentage = maxQuotaBytes > 0 ? Math.min(100, Math.round((currentQuotaBytes / maxQuotaBytes) * 100)) : 0

	let barColor = 'bg-indigo-600'
	if (percentage > 90) barColor = 'bg-rose-500'
	else if (percentage > 70) barColor = 'bg-amber-500'

	return (
		<aside className="flex w-64 flex-col justify-between border-r border-gray-200 bg-gray-50/50 p-4 dark:border-neutral-800 dark:bg-neutral-900/50">
			{/* Menu items */}
			<nav className="space-y-1.5">
				<button
					type="button"
					onClick={() => onSelectTab('files')}
					className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition ${
						currentTab === 'files'
							? 'bg-indigo-50 text-indigo-700 shadow-sm dark:bg-indigo-950/50 dark:text-indigo-300'
							: 'text-gray-600 hover:bg-gray-100 dark:text-neutral-400 dark:hover:bg-neutral-800'
					}`}
				>
					<span className="text-base">📁</span>
					<span>Tệp của tôi</span>
				</button>

				<button
					type="button"
					onClick={() => onSelectTab('starred')}
					className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition ${
						currentTab === 'starred'
							? 'bg-indigo-50 text-indigo-700 shadow-sm dark:bg-indigo-950/50 dark:text-indigo-300'
							: 'text-gray-600 hover:bg-gray-100 dark:text-neutral-400 dark:hover:bg-neutral-800'
					}`}
				>
					<span className="text-base">⭐</span>
					<span>Đã gắn sao</span>
				</button>

				<button
					type="button"
					onClick={() => onSelectTab('trash')}
					className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition ${
						currentTab === 'trash'
							? 'bg-indigo-50 text-indigo-700 shadow-sm dark:bg-indigo-950/50 dark:text-indigo-300'
							: 'text-gray-600 hover:bg-gray-100 dark:text-neutral-400 dark:hover:bg-neutral-800'
					}`}
				>
					<span className="text-base">🗑️</span>
					<span>Thùng rác</span>
				</button>
			</nav>

			{/* Quota Section */}
			<div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm dark:border-neutral-800 dark:bg-neutral-900">
				<div className="flex items-center justify-between text-xs font-semibold text-gray-700 dark:text-neutral-300">
					<span>Dung lượng lưu trữ</span>
					<button
						type="button"
						onClick={refreshQuota}
						title="Làm mới quota"
						className="text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400"
					>
						🔄
					</button>
				</div>

				<div className="mt-2.5 h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-neutral-800">
					<div
						className={`h-full transition-all duration-300 ${barColor}`}
						style={{ width: `${Math.max(percentage, 2)}%` }}
					/>
				</div>

				<div className="mt-2 flex items-center justify-between text-[11px] text-gray-500 dark:text-neutral-400">
					<span>{formatBytes(currentQuotaBytes)} đã dùng</span>
					<span className="font-semibold">{percentage}%</span>
				</div>

				<div className="mt-1 text-[11px] text-gray-400 dark:text-neutral-500">
					Tổng dung lượng: {formatBytes(maxQuotaBytes)}
				</div>
			</div>
		</aside>
	)
}
