#!/usr/bin/env bash
set -euo pipefail

echo "Starting devcontainer setup..."

# Create necessary directories
echo "Creating configuration directories..."
mkdir -p "${HOME}/.claude" "${HOME}/.codex"

# Install shell tools
echo "Installing fzf and zoxide..."
apt-get update
apt-get install -y fzf zoxide

# Install GitHub CLI
echo "Installing GitHub CLI..."
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null
apt-get update
apt-get install -y gh

# Configure zoxide for bash and zsh
echo "Configuring zoxide..."
echo 'eval "$(zoxide init bash)"' >> "${HOME}/.bashrc"
if [ -f "${HOME}/.zshrc" ]; then
  echo 'eval "$(zoxide init zsh)"' >> "${HOME}/.zshrc"
fi

# Install and configure mise
echo "Installing mise..."
curl https://mise.run | sh
if [ -f /workspace/.mise.toml ] || [ -f /workspace/.tool-versions ]; then
  mise trust /workspace --yes
  mise install
fi

# Install pnpm globally (useful for Node.js tooling)
echo "Installing pnpm..."
npm install -g pnpm

echo "Installing Claude Code CLI..."
npm install -g @anthropic-ai/claude-code || echo "Warning: Failed to install Claude Code CLI"

echo "Installing codex CLI..."
npm install -g @openai/codex || echo "Warning: Failed to install codex CLI"

# Verify Java development tools
echo "Verifying Java development environment..."
java -version
mvn -version

echo "Devcontainer setup completed!"
