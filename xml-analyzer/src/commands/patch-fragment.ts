import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import { FragmentEditor } from '../webview/fragment-editor';

export async function registerPatchCommand(
    context: vscode.ExtensionContext,
    client: LanguageClient,
    fragmentEditor: FragmentEditor
): Promise<void> {
    context.subscriptions.push(
        vscode.commands.registerCommand('xml.patchFragment', async (error: any) => {
            if (!error?.fragment) return;

            try {
                const modified = await fragmentEditor.openFragmentEditor(
                    error.fragment,
                    error.message,
                    error.line,
                    error.filePath
                );
                if (modified === null) return;

                // ----  cast explicite  ----
                const result: any = await client.sendRequest('xml/patchFragment', {
                    filePath: error.filePath,
                    modifiedFragment: modified,
                    fragmentStartLine: error.fragmentStartLine,
                    fragmentEndLine: error.fragmentEndLine,
                });

                if (result?.success) {
                    vscode.window.showInformationMessage('Fragment sauvegardé avec succès');
                    await vscode.commands.executeCommand('xml.validateFiles', error.filePath);
                } else {
                    vscode.window.showErrorMessage(
                        `Erreur lors de la sauvegarde : ${result?.error ?? 'Erreur inconnue'}`
                    );
                }
            } catch (e) {
                vscode.window.showErrorMessage(`Erreur : ${e}`);
            }
        })
    );
}