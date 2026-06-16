<style scoped lang="scss">
.issue-card-wrapper {
  position: relative;
  margin-bottom: 8px;
  border-radius: var(--border-radius-md);
  transition: border-color 0.15s;

  &:hover {
    border-color: rgba(0,0,0,0.15);
  }
}

.issue-card__actions {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  gap: 4px;
  z-index: 10;
}

.issue-card__action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 7px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.15s ease;

  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
    background: rgba(79,70,229,0.04);
  }

  &--done:hover { color: var(--color-success); }
  &--danger:hover { color: var(--color-danger, #ef4444); }
}

.actions-fade-enter-active,
.actions-fade-leave-active {
  transition: opacity 0.15s ease;
}
.actions-fade-enter-from,
.actions-fade-leave-to {
  opacity: 0;
}
</style>
