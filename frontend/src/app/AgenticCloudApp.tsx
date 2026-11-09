import React, { useState } from 'react'
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
import { Sidebar } from './components/Sidebar'

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
	const [currentTab, setCurrentTab] = useState<'files' | 'starred' | 'trash'>('files')

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

			<div className="flex flex-1 overflow-hidden">
				<Sidebar currentTab={currentTab} onSelectTab={setCurrentTab} />

				<main className="relative flex-1 overflow-hidden bg-white p-4 dark:bg-neutral-900">
					<FileBrowserProvider>
						<FileBrowser
							adapter={adapter}
							rootLabel={user?.username ? `${user.username}'s Drive` : 'Tệp của tôi'}
							previewers={PREVIEWERS}
							editors={EDITORS}
							initialView="list"
							density="comfortable"
						/>
					</FileBrowserProvider>
				</main>
			</div>
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
