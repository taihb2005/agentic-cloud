import React, { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export const AuthModal: React.FC<{ onSkipDemo?: () => void }> = ({ onSkipDemo }) => {
	const { login, register, error } = useAuth()
	const [mode, setMode] = useState<'login' | 'register'>('login')
	const [username, setUsername] = useState('alice')
	const [email, setEmail] = useState('alice@example.com')
	const [password, setPassword] = useState('password123')
	const [isSubmitting, setIsSubmitting] = useState(false)
	const [localError, setLocalError] = useState<string | null>(null)

	const handleSubmit = async (e: React.FormEvent) => {
		e.preventDefault()
		setLocalError(null)
		setIsSubmitting(true)
		try {
			if (mode === 'login') {
				await login(username, password)
			} else {
				await register(username, email, password)
			}
		} catch (err: unknown) {
			setLocalError(err instanceof Error ? err.message : 'Thao tác không thành công')
		} finally {
			setIsSubmitting(false)
		}
	}

	return (
		<div className="flex min-h-[480px] w-full max-w-md flex-col justify-center rounded-2xl border border-gray-200 bg-white p-8 shadow-xl dark:border-neutral-800 dark:bg-neutral-900">
			<div className="mb-6 text-center">
				<div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-600 text-2xl text-white shadow-lg shadow-indigo-500/30">
					☁️
				</div>
				<h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
					Agentic Cloud Storage
				</h2>
				<p className="mt-1 text-sm text-gray-500 dark:text-neutral-400">
					{mode === 'login' ? 'Đăng nhập vào tài khoản lưu trữ đám mây' : 'Tạo tài khoản lưu trữ mới'}
				</p>
			</div>

			<div className="mb-6 flex rounded-xl bg-gray-100 p-1 dark:bg-neutral-800">
				<button
					type="button"
					onClick={() => {
						setMode('login')
						setLocalError(null)
					}}
					className={`w-1/2 rounded-lg py-2 text-sm font-semibold transition-all ${
						mode === 'login'
							? 'bg-white text-indigo-600 shadow-sm dark:bg-neutral-700 dark:text-white'
							: 'text-gray-500 hover:text-gray-900 dark:text-neutral-400 dark:hover:text-white'
					}`}
				>
					Đăng nhập
				</button>
				<button
					type="button"
					onClick={() => {
						setMode('register')
						setLocalError(null)
					}}
					className={`w-1/2 rounded-lg py-2 text-sm font-semibold transition-all ${
						mode === 'register'
							? 'bg-white text-indigo-600 shadow-sm dark:bg-neutral-700 dark:text-white'
							: 'text-gray-500 hover:text-gray-900 dark:text-neutral-400 dark:hover:text-white'
					}`}
				>
					Đăng ký
				</button>
			</div>

			{(localError || error) && (
				<div className="mb-4 rounded-xl bg-rose-50 p-3.5 text-sm text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
					{localError || error}
				</div>
			)}

			<form onSubmit={handleSubmit} className="space-y-4">
				<div>
					<label className="block text-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-neutral-300">
						Tên đăng nhập
					</label>
					<input
						type="text"
						value={username}
						onChange={(e) => setUsername(e.target.value)}
						required
						className="mt-1.5 w-full rounded-xl border border-gray-300 bg-white px-3.5 py-2.5 text-sm text-gray-900 outline-none transition focus:border-indigo-600 focus:ring-2 focus:ring-indigo-600/20 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
						placeholder="Nhập tên đăng nhập..."
					/>
				</div>

				{mode === 'register' && (
					<div>
						<label className="block text-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-neutral-300">
							Email
						</label>
						<input
							type="email"
							value={email}
							onChange={(e) => setEmail(e.target.value)}
							required
							className="mt-1.5 w-full rounded-xl border border-gray-300 bg-white px-3.5 py-2.5 text-sm text-gray-900 outline-none transition focus:border-indigo-600 focus:ring-2 focus:ring-indigo-600/20 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
							placeholder="name@example.com"
						/>
					</div>
				)}

				<div>
					<label className="block text-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-neutral-300">
						Mật khẩu
					</label>
					<input
						type="password"
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						required
						minLength={6}
						className="mt-1.5 w-full rounded-xl border border-gray-300 bg-white px-3.5 py-2.5 text-sm text-gray-900 outline-none transition focus:border-indigo-600 focus:ring-2 focus:ring-indigo-600/20 dark:border-neutral-700 dark:bg-neutral-800 dark:text-white"
						placeholder="••••••••"
					/>
				</div>

				<button
					type="submit"
					disabled={isSubmitting}
					className="w-full rounded-xl bg-indigo-600 py-3 text-sm font-semibold text-white shadow-md shadow-indigo-600/20 transition hover:bg-indigo-700 disabled:opacity-50"
				>
					{isSubmitting ? 'Đang xử lý...' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
				</button>
			</form>

			{onSkipDemo && (
				<div className="mt-6 border-t border-gray-100 pt-4 text-center dark:border-neutral-800">
					<button
						type="button"
						onClick={onSkipDemo}
						className="text-xs font-medium text-gray-500 hover:text-indigo-600 dark:text-neutral-400 dark:hover:text-indigo-400"
					>
						⚡ Tiếp tục xem Demo In-Memory (Không kết nối)
					</button>
				</div>
			)}
		</div>
	)
}
