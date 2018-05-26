import sys

from git.exc import GitCommandError, InvalidGitRepositoryError
from git.repo.base import Repo

from tools.user_interface import print_info, print_error, print_info_dry, prompt_yesno_question
from tools.config import Config


class GitRepo:

    def __init__(self, config: Config) -> None:
        self.__config: Config = config

        try:
            self.repo = Repo(config.root_path)
            assert not self.repo.bare
            self.origin = self.repo.remote('origin')
        except InvalidGitRepositoryError:
            print_error("Path is not a git repository. Please go to valid git repository!")
            sys.exit()

    def pull(self):
        try:
            print_info('Pull changes from origin ...')
            self.origin.pull()
        except GitCommandError:
            print_error(
                "Pull is not possible because you have unmerged files. Fix them up in the work tree, and then try again.")
            sys.exit()

    def reset(self):
        if(prompt_yesno_question('Should the repository and file system to be reset automatically before exiting?')):
            # arbitrary 20, but extensive enough to reset all hopefully
            print_info("Executing reset (git reset --hard HEAD~20)")
            self.repo.git.reset('--hard HEAD~20')
            self.update_and_clean()

    def update_and_clean(self):
        print_info("Executing update and cleanup (git pull origin && git submodule update && git clean -fd)")
        self.origin.pull()
        self.repo.execute("git submodule update")
        self.repo.execute("git clean -f -d")

    def checkout(self, branch_name):
        print_info("Checkout " + branch_name)
        self.repo.git.checkout(branch_name)

    def commit(self, commit_message: str):
        try:
            print_info("Committing ...")
            self.repo.git.commit(message="#" + str(self.__config.github_issue_no) + " " + commit_message)
            self.push()
        except Exception as e:
            if "no changes added to commit" in str(e):
                print_info("No File is changed, Nothing to commit..")

    def push(self):
        ''' Boolean return type states, whether to continue process or abort'''
        if(self.__config.debug):
            if not prompt_yesno_question("Changes will be pushed now. Continue?"):
                self.reset()
                sys.exit()
        if(self.__config.dry_run):
            print_info_dry('Skipping pushing of changes.')
            return

        try:
            print_info("Pushing ...")
            self.origin.push(tags=True)
        except Exception as e:
            if "no changes added to commit" in str(e):
                print_info("No file is changed, nothing to commit.")
            else:
                raise e
