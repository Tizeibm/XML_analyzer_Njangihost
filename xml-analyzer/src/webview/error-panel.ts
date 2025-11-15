import * as vscode from 'vscode';

export interface ValidationError {
    id: string;
    line: number;
    column: number;
    message: string;
    severity: 'error' | 'warning';
    code: string;
    fragment: string;
    fragmentStartLine: number;
    fragmentEndLine: number;
}

export interface ValidationResults {
    filePath: string;
    isValid: boolean;
    totalErrors: number;
    totalWarnings: number;
    errors: ValidationError[];
    warnings: ValidationError[];
}

export class ErrorPanelProvider implements vscode.WebviewViewProvider {
    public static readonly viewType = 'xml-validator.errorPanel';
    private view?: vscode.WebviewView;
    private validationResults: ValidationResults | null = null;
    private onSelectError: (e: ValidationError, p: string) => void = () => {};

    constructor(private extensionUri: vscode.Uri) {}

    resolveWebviewView(
        wv: vscode.WebviewView,
        _ctx: vscode.WebviewViewResolveContext,
        _token: vscode.CancellationToken
    ): void {
        this.view = wv;
        wv.webview.options = {
            enableScripts: true,
            localResourceRoots: [this.extensionUri]
        };
        wv.webview.html = this.getHtml(wv.webview);

        wv.webview.onDidReceiveMessage(msg => {
            if (msg.command === 'selectError') {
                const err =
                    this.validationResults?.errors.find(e => e.id === msg.errorId) ??
                    this.validationResults?.warnings.find(e => e.id === msg.errorId);
                if (err && this.validationResults) {
                    this.onSelectError(err, this.validationResults.filePath);
                }
            }
        });
    }

    updateResults(r: ValidationResults): void {
        this.validationResults = r;
        this.view?.webview.postMessage({ command: 'updateResults', results: r });
    }

    setOnSelectError(cb: (e: ValidationError, p: string) => void): void {
        this.onSelectError = cb;
    }

    private getHtml(wv: vscode.Webview): string {
        const nonce = this.nonce();
        return `<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline';">
    <title>Validateur XML</title>
    <style>
        body{font-family:var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);padding:0;margin:0}
        .container{padding:12px}
        .header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;border-bottom:1px solid var(--vscode-widget-border);padding-bottom:8px}
        .stats{display:flex;gap:16px;font-size:12px}
        .stat-item{display:flex;align-items:center;gap:4px}
        .error-icon{color:#f48771}
        .warning-icon{color:#dcdcaa}
        .check-icon{color:#4ec9b0}
        .error-list{display:flex;flex-direction:column;gap:8px}
        .error-item{padding:8px;background:var(--vscode-editor-lineHighlightBackground);border-left:3px solid #f48771;border-radius:2px;cursor:pointer}
        .error-item.warning{border-left-color:#dcdcaa}
        .error-item.success{border-left-color:#4ec9b0}
        .error-header{display:flex;justify-content:space-between;align-items:start;margin-bottom:4px}
        .error-location{font-weight:bold;font-size:12px;color:var(--vscode-symbolIcon-functionForeground)}
        .error-message{font-size:13px;margin:4px 0;line-height:1.4}
        .error-code{font-size:11px;color:var(--vscode-descriptionForeground);font-family:monospace}
        .empty-state{text-align:center;color:var(--vscode-descriptionForeground);padding:32px 16px}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <span style="font-weight:bold;font-size:14px">Validation XML</span>
            <div class="stats" id="stats"></div>
        </div>
        <div class="error-list" id="errorList"></div>
        <div class="empty-state" id="emptyState">Aucune validation en cours</div>
    </div>
    <script nonce="${nonce}">
        const vscode=acquireVsCodeApi();
        let current=null;
        window.addEventListener('message',e=>{
            if(e.data.command==='updateResults'){current=e.data.results;render();}
        });
        function render(){
            const list=document.getElementById('errorList');
            const empty=document.getElementById('emptyState');
            const stats=document.getElementById('stats');
            if(!current){empty.style.display='block';list.innerHTML='';stats.innerHTML='';return;}
            empty.style.display='none';
            stats.innerHTML=\`
                \${current.isValid?'<span class="stat-item"><span class="check-icon">✓</span> Valide</span>':''}
                <span class="stat-item"><span class="error-icon">✕</span> \${current.totalErrors}</span>
                <span class="stat-item"><span class="warning-icon">⚠</span> \${current.totalWarnings}</span>
            \`;
            const all=[...current.errors,...current.warnings];
            list.innerHTML=all.map(e=>\`
                <div class="error-item \${e.severity==='warning'?'warning':''}" onclick="selectError('\${e.id}')">
                    <div class="error-header">
                        <span class="error-location">Ligne \${e.line}, Col \${e.column}</span>
                    </div>
                    <div class="error-message">\${escape(e.message)}</div>
                    <div class="error-code">[\${e.code}]</div>
                </div>
            \`).join('');
        }
        function selectError(id){vscode.postMessage({command:'selectError',errorId:id});}
        function escape(t){const d=document.createElement('div');d.textContent=t;return d.innerHTML;}
        render();
    </script>
</body>
</html>`;
    }

    private nonce(): string {
        return Array.from({ length: 16 })
            .map(() => 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'[Math.floor(Math.random() * 62)])
            .join('');
    }
}