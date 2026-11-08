import React from 'react'
import { useAuth } from '../context/AuthContext'

export const Header: React.FC<{ onOpenDemoSettings?: () => void }> = () => {
	const { user, logout, client } = useAuth()

	return (
		<header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6 dark:border-neutral-800 dark:bg-neutral-900">
			<div className="flex items-center gap-3">
				<div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 font-bold text-white shadow-md shadow-indigo-600/30">
					☁️
				</div>
				<div>
					<h1 className="text-base font-bold leading-tight text-gray-900 dark:text-white">
						Agentic Cloud
					</h1>
					<div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-neutral-400">
						<span className="inline-block h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
						<span>Cổng Gateway: <code className="font-mono text-[11px] text-gray-600 dark:text-neutral-300">{client.getBaseUrl()}</code></span>
					</div>
				</div>
			</div>

			<div className="flex items-center gap-4">
				{user && (
					<div className="flex items-center gap-3">
						<div className="hidden text-right sm:block">
							<div className="text-sm font-semibold text-gray-900 dark:text-white">
								{user.username}
							</div>
							<div className="text-xs text-gray-500 dark:text-neutral-400">
								{user.email}
							</div>
						</div>
						<div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-100 font-semibold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
							{user.username.charAt(0).toUpperCase()}
						</div>
						<button
							type="button"
							onClick={logout}
							title="Đăng xuất"
							className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-600 transition hover:bg-gray-100 dark:border-neutral-700 dark:text-neutral-300 dark:hover:bg-neutral-800"
						>
							Đăng xuất
						</button>
					</div>
				)}
			</div>
		</header>
	)
}
