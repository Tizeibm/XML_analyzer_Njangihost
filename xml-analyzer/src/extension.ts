import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';
import * as path from 'path';
import { ErrorPanelProvider } from './webview/error-panel';
import { FragmentEditor } from './webview/fragment-editor';
import { registerPatchCommand } from './commands/patch-fragment';

let client: LanguageClient;
let errorPanelProvider: ErrorPanelProvider;
let fragmentEditor: FragmentEditor;

export function activate(context: vscode.ExtensionContext): void {
    const extUri = context.extensionUri;
    errorPanelProvider = new ErrorPanelProvider(extUri);
    fragmentEditor = new FragmentEditor(extUri, context);

    context.subscriptions.push(
        vscode.window.registerWebviewViewProvider(ErrorPanelProvider.viewType, errorPanelProvider)
    );

    startLanguageServer(context);

    if (client) {
        client.onNotification('xml/validationResults', (r) => errorPanelProvider.updateResults(r));
    }

    registerCommands(context);
}

function startLanguageServer(ctx: vscode.ExtensionContext): void {
    const serverModule = ctx.asAbsolutePath(path.join('server', 'xml-validator-server.jar'));
    const serverOptions: ServerOptions = {
        run: { command: 'java', args: ['-jar', serverModule], options: { cwd: ctx.extensionPath } },
        debug: {
            command: 'java',
            args: ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=6009', '-jar', serverModule],
            options: { cwd: ctx.extensionPath }
        }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xml' }, { scheme: 'file', pattern: '**/*.xml' }],
        synchronize: { fileEvents: vscode.workspace.createFileSystemWatcher('**/*.xml') }
    };

    client = new LanguageClient('xml-validator-server', 'XML Validator Server', serverOptions, clientOptions);
    client.start();
}

function registerCommands(ctx: vscode.ExtensionContext): void {
    ctx.subscriptions.push(
        vscode.commands.registerCommand('xml.validateFiles', async () => {
            const files = await vscode.window.showOpenDialog({
                canSelectMany: true,
                filters: { 'XML Files': ['xml'] },
                openLabel: 'Valider'
            });
            if (!files) return;
            try {
                await client.sendRequest('xml/validateFiles', {
                    filePaths: files.map(f => f.fsPath)
                });
            } catch (e) {
                vscode.window.showErrorMessage(`Erreur de validation : ${e}`);
            }
        })
    );

    registerPatchCommand(ctx, client, fragmentEditor);

    ctx.subscriptions.push(
        vscode.commands.registerCommand('xml.openValidator', () =>
            vscode.commands.executeCommand('workbench.view.extension.xml-validator-view')
        )
    );

    errorPanelProvider.setOnSelectError((err, filePath) =>
        vscode.commands.executeCommand('xml.patchFragment', { ...err, filePath })
    );
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}