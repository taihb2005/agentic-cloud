import React, { createContext, useContext, useState, useCallback, useRef } from 'react'
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X } from 'lucide-react'

export interface ToastItem {
	id: string
	type: 'success' | 'error' | 'warning' | 'info'
	message: string
	title?: string
	duration: number
}

export interface ConfirmOptions {
	title?: string
	message: string
	confirmText?: string
	cancelText?: string
	type?: 'danger' | 'warning' | 'info'
}

interface NotificationContextType {
	showToast: (options: {
		type?: 'success' | 'error' | 'warning' | 'info'
		message: string
		title?: string
		duration?: number
	}) => void
	confirm: (options: ConfirmOptions) => Promise<boolean>
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined)

const ToastCard: React.FC<{
	toast: ToastItem
	onDismiss: (id: string) => void
}> = ({ toast, onDismiss }) => {
	const [progress, setProgress] = React.useState(100)

	React.useEffect(() => {
		const startTime = Date.now()
		const interval = setInterval(() => {
			const elapsed = Date.now() - startTime
			const remaining = Math.max(0, 100 - (elapsed / toast.duration) * 100)
			setProgress(remaining)
			if (remaining <= 0) {
				clearInterval(interval)
				onDismiss(toast.id)
			}
		}, 25)

		return () => clearInterval(interval)
	}, [toast.duration, toast.id, onDismiss])

	const config = {
		success: {
			border: 'border-emerald-200 dark:border-emerald-800/60',
			bg: 'bg-emerald-50/95 dark:bg-emerald-950/90',
			text: 'text-emerald-900 dark:text-emerald-100',
			subtext: 'text-emerald-700 dark:text-emerald-300',
			bar: 'bg-emerald-500',
			icon: <CheckCircle2 className="size-5 shrink-0 text-emerald-600 dark:text-emerald-400" />
		},
		error: {
			border: 'border-rose-200 dark:border-rose-800/60',
			bg: 'bg-rose-50/95 dark:bg-rose-950/90',
			text: 'text-rose-900 dark:text-rose-100',
			subtext: 'text-rose-700 dark:text-rose-300',
			bar: 'bg-rose-500',
			icon: <AlertCircle className="size-5 shrink-0 text-rose-600 dark:text-rose-400" />
		},
		warning: {
			border: 'border-amber-200 dark:border-amber-800/60',
			bg: 'bg-amber-50/95 dark:bg-amber-950/90',
			text: 'text-amber-900 dark:text-amber-100',
			subtext: 'text-amber-700 dark:text-amber-300',
			bar: 'bg-amber-500',
			icon: <AlertTriangle className="size-5 shrink-0 text-amber-600 dark:text-amber-400" />
		},
		info: {
			border: 'border-indigo-200 dark:border-indigo-800/60',
			bg: 'bg-indigo-50/95 dark:bg-indigo-950/90',
			text: 'text-indigo-900 dark:text-indigo-100',
			subtext: 'text-indigo-700 dark:text-indigo-300',
			bar: 'bg-indigo-500',
			icon: <Info className="size-5 shrink-0 text-indigo-600 dark:text-indigo-400" />
		}
	}[toast.type]

	return (
		<div
			className={`pointer-events-auto relative flex w-80 max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border shadow-xl backdrop-blur-md transition-all duration-300 ${config.bg} ${config.border}`}
			style={{
				animation: 'slideInFromLeft 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards'
			}}
		>
			<div className="flex items-start gap-3 p-3.5">
				{config.icon}
				<div className="flex-1 min-w-0">
					{toast.title && <div className={`text-xs font-bold ${config.text}`}>{toast.title}</div>}
					<div className={`text-xs font-medium leading-relaxed ${config.subtext}`}>{toast.message}</div>
				</div>
				<button
					type="button"
					onClick={() => onDismiss(toast.id)}
					className="rounded-lg p-1 text-gray-400 hover:bg-black/5 hover:text-gray-700 dark:hover:bg-white/10 dark:hover:text-gray-200 transition"
					title="Đóng thông báo"
				>
					<X className="size-3.5" />
				</button>
			</div>

			{/* Thanh chạy đếm ngược (Progress countdown bar) */}
			<div className="h-1 w-full bg-black/5 dark:bg-white/10">
				<div
					className={`h-full transition-all duration-[25ms] ease-linear ${config.bar}`}
					style={{ width: `${progress}%` }}
				/>
			</div>
		</div>
	)
}

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
	const [toasts, setToasts] = useState<ToastItem[]>([])
	const [confirmState, setConfirmState] = useState<{
		isOpen: boolean
		options: ConfirmOptions
		resolve: (val: boolean) => void
	} | null>(null)

	const toastIdSeq = useRef(0)

	const showToast = useCallback(
		({
			type = 'info',
			message,
			title,
			duration = 4000
		}: {
			type?: 'success' | 'error' | 'warning' | 'info'
			message: string
			title?: string
			duration?: number
		}) => {
			const id = `toast-${Date.now()}-${++toastIdSeq.current}`
			const newToast: ToastItem = {
				id,
				type,
				message,
				title,
				duration
			}
			setToasts((prev) => [newToast, ...prev].slice(0, 5))
		},
		[]
	)

	const dismissToast = useCallback((id: string) => {
		setToasts((prev) => prev.filter((t) => t.id !== id))
	}, [])

	const confirm = useCallback((options: ConfirmOptions): Promise<boolean> => {
		return new Promise((resolve) => {
			setConfirmState({
				isOpen: true,
				options,
				resolve
			})
		})
	}, [])

	const handleConfirmAction = (accepted: boolean) => {
		if (confirmState) {
			confirmState.resolve(accepted)
			setConfirmState(null)
		}
	}

	return (
		<NotificationContext.Provider value={{ showToast, confirm }}>
			{children}

			{/* CSS Keyframes for Animations */}
			<style>{`
				@keyframes slideInFromLeft {
					from {
						opacity: 0;
						transform: translateX(-30px) scale(0.95);
					}
					to {
						opacity: 1;
						transform: translateX(0) scale(1);
					}
				}
				@keyframes fadeInModal {
					from {
						opacity: 0;
						transform: scale(0.96);
					}
					to {
						opacity: 1;
						transform: scale(1);
					}
				}
			`}</style>

			{/* Top-Left Snackbar Container */}
			<div className="fixed top-5 left-5 z-[9999] flex flex-col gap-2.5 pointer-events-none">
				{toasts.map((toast) => (
					<ToastCard key={toast.id} toast={toast} onDismiss={dismissToast} />
				))}
			</div>

			{/* Confirm Popup Modal */}
			{confirmState?.isOpen && (
				<div
					className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
					onClick={() => handleConfirmAction(false)}
				>
					<div
						className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-6 shadow-2xl dark:border-neutral-800 dark:bg-neutral-900"
						style={{ animation: 'fadeInModal 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards' }}
						onClick={(e) => e.stopPropagation()}
					>
						<div className="flex items-start gap-4">
							<div
								className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${
									confirmState.options.type === 'danger'
										? 'bg-rose-50 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400'
										: confirmState.options.type === 'warning'
											? 'bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-400'
											: 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400'
								}`}
							>
								{confirmState.options.type === 'danger' ? (
									<AlertCircle className="size-6" />
								) : confirmState.options.type === 'warning' ? (
									<AlertTriangle className="size-6" />
								) : (
									<Info className="size-6" />
								)}
							</div>

							<div className="flex-1">
								<h3 className="text-base font-bold text-gray-900 dark:text-white">
									{confirmState.options.title || 'Xác nhận hành động'}
								</h3>
								<p className="mt-1.5 text-xs text-gray-500 leading-relaxed dark:text-neutral-400">
									{confirmState.options.message}
								</p>
							</div>
						</div>

						<div className="mt-6 flex justify-end gap-2.5">
							<button
								type="button"
								onClick={() => handleConfirmAction(false)}
								className="rounded-xl border border-gray-200 px-4 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-300 dark:hover:bg-neutral-700 transition"
							>
								{confirmState.options.cancelText || 'Hủy bỏ'}
							</button>
							<button
								type="button"
								autoFocus
								onClick={() => handleConfirmAction(true)}
								className={`rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-md transition ${
									confirmState.options.type === 'danger'
										? 'bg-rose-600 hover:bg-rose-700 shadow-rose-600/20'
										: confirmState.options.type === 'warning'
											? 'bg-amber-600 hover:bg-amber-700 shadow-amber-600/20'
											: 'bg-indigo-600 hover:bg-indigo-700 shadow-indigo-600/20'
								}`}
							>
								{confirmState.options.confirmText || 'Xác nhận'}
							</button>
						</div>
					</div>
				</div>
			)}
		</NotificationContext.Provider>
	)
}

export const useNotification = () => {
	const ctx = useContext(NotificationContext)
	if (!ctx) {
		throw new Error('useNotification must be used within a NotificationProvider')
	}
	return ctx
}
