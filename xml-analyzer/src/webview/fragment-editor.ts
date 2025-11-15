import * as vscode from 'vscode';

export class FragmentEditor {
    private panel: vscode.WebviewPanel | undefined;

    constructor(
        private extensionUri: vscode.Uri,
        private context: vscode.ExtensionContext
    ) {}

    async openFragmentEditor(
        fragment: string,
        errorMessage: string,
        line: number,
        filePath: string
    ): Promise<string | null> {
        if (this.panel) {
            this.panel.reveal(vscode.ViewColumn.Active);
            return new Promise(res => this.waitForClose(res));
        }

        this.panel = vscode.window.createWebviewPanel(
            'xml-fragment-editor',
            `Éditer fragment XML (Ligne ${line})`,
            vscode.ViewColumn.Active,
            { enableScripts: true, retainContextWhenHidden: true }
        );

        let saved = false;
        let content = fragment;

        this.panel.webview.html = this.getHtml(content, errorMessage, line);

        this.panel.webview.onDidReceiveMessage(msg => {
            if (msg.command === 'save') {
                if (this.isValidXml(msg.content)) {
                    saved = true;
                    content = msg.content;
                    this.panel!.dispose();
                } else {
                    vscode.window.showErrorMessage('XML invalide : balises mal fermées ou syntaxe incorrecte');
                }
            }
            if (msg.command === 'cancel') {
                this.panel!.dispose();
            }
        });

        return new Promise<string | null>(res => {
            this.panel!.onDidDispose(() => {
                this.panel = undefined;
                res(saved ? content : null);
            });
        });
    }

    private waitForClose(cb: (v: string | null) => void): void {
        const disp = this.panel!.onDidDispose(() => {
            disp.dispose();
            cb(null);
        });
    }

    private isValidXml(xml: string): boolean {
        try {
            const stack: string[] = [];
            const tagRegex = /<(\/?[a-zA-Z0-9:_-]+)[^>]*>/g;
            let m: RegExpExecArray | null;
            while ((m = tagRegex.exec(xml)) !== null) {
                const tag = m[1];
                if (tag.startsWith('/')) {
                    if (stack.pop() !== tag.slice(1)) return false;
                } else if (!m[0].endsWith('/>')) {
                    stack.push(tag);
                }
            }
            return stack.length === 0;
        } catch {
            return false;
        }
    }

    private getHtml(fragment: string, msg: string, line: number): string {
        const nonce = Math.random().toString(36).slice(-12);
        const safeMsg = JSON.stringify(msg);
        return `<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline';">
    <title>Éditeur de Fragment XML</title>
    <style>
        body{font-family:var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:0;padding:0;display:flex;flex-direction:column;height:100vh}
        .header{background:var(--vscode-editor-lineHighlightBackground);padding:12px;border-bottom:1px solid var(--vscode-widget-border)}
        .error-box{background:rgba(244,135,113,.1);border-left:3px solid #f48771;padding:8px 12px;margin-bottom:8px;font-size:12px;color:#f48771}
        .content{flex:1;display:flex;flex-direction:column;padding:12px;overflow:hidden}
        textarea{flex:1;background:var(--vscode-editor-background);color:var(--vscode-editor-foreground);border:1px solid var(--vscode-widget-border);font-family:var(--vscode-editor-font-family,monospace);font-size:13px;padding:8px;resize:none;line-height:1.5}
        textarea:focus{outline:none;border-color:var(--vscode-focusBorder)}
        .footer{display:flex;gap:8px;padding:12px;border-top:1px solid var(--vscode-widget-border)}
        button{padding:6px 16px;border:1px solid var(--vscode-button-border);border-radius:2px;cursor:pointer;font-size:12px;font-family:var(--vscode-font-family)}
        .btn-save{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}
        .btn-save:hover{background:var(--vscode-button-hoverBackground)}
        .btn-cancel{background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground)}
        .btn-cancel:hover{background:var(--vscode-button-secondaryHoverBackground)}
    </style>
</head>
<body>
    <div class="header">
        <div class="error-box">Ligne ${line}: <span id="msg"></span></div>
        <small style="color:var(--vscode-descriptionForeground)">Modifiez le fragment et cliquez Sauvegarder pour appliquer les changements</small>
    </div>
    <div class="content">
        <textarea id="txt" spellcheck="false"></textarea>
    </div>
    <div class="footer">
        <button class="btn-save" onclick="save()">Sauvegarder</button>
        <button class="btn-cancel" onclick="cancel()">Annuler</button>
    </div>
    <script nonce="${nonce}">
        const vscode=acquireVsCodeApi();
        const txt=document.getElementById('txt');
        document.getElementById('msg').textContent=${safeMsg};
        txt.value=${JSON.stringify(fragment)};
        txt.addEventListener('input',()=>vscode.postMessage({command:'updateFragment',content:txt.value}));
        function save(){vscode.postMessage({command:'save',content:txt.value});}
        function cancel(){vscode.postMessage({command:'cancel'});}
        txt.focus();
    </script>
</body>
</html>`;
    }
}