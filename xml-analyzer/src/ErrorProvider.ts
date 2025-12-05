import * as vscode from 'vscode';

export class ErrorProvider implements vscode.TreeDataProvider<ErrorItem> {
    private _onDidChangeTreeData: vscode.EventEmitter<ErrorItem | undefined | null | void> = new vscode.EventEmitter<ErrorItem | undefined | null | void>();
    readonly onDidChangeTreeData: vscode.Event<ErrorItem | undefined | null | void> = this._onDidChangeTreeData.event;

    private errors: any[] = [];

    constructor() { }

    refresh(errors: any[]): void {
        this.errors = errors;
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: ErrorItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: ErrorItem): Thenable<ErrorItem[]> {
        if (element) {
            return Promise.resolve([]);
        } else {
            return Promise.resolve(
                this.errors.map(err => new ErrorItem(
                    err.message,
                    err.lineNumber,
                    err.type,
                    err.severity,
                    err.fragment, // fragmentId
                    vscode.TreeItemCollapsibleState.None
                ))
            );
        }
    }
}

export class ErrorItem extends vscode.TreeItem {
    constructor(
        public readonly message: string,
        public readonly lineNumber: number,
        public readonly type: string,
        public readonly severity: string,
        public readonly fragmentId: string | undefined,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState
    ) {
        super(`[Line ${lineNumber}] ${message}`, collapsibleState);
        this.tooltip = `${this.message} (${this.type})`;
        this.description = this.type;

        // Set icon based on severity
        if (severity === 'ERROR' || severity === 'FATAL') {
            this.iconPath = new vscode.ThemeIcon('error', new vscode.ThemeColor('problemsErrorIcon.foreground'));
        } else if (severity === 'WARNING') {
            this.iconPath = new vscode.ThemeIcon('warning', new vscode.ThemeColor('problemsWarningIcon.foreground'));
        } else {
            this.iconPath = new vscode.ThemeIcon('info', new vscode.ThemeColor('problemsInfoIcon.foreground'));
        }

        // Context value for menu actions
        this.contextValue = 'errorItem';

        // Bind click action to preview fragment
        this.command = {
            command: 'xml.previewFragment',
            title: 'Preview Fragment',
            arguments: [this]
        };
    }
}
